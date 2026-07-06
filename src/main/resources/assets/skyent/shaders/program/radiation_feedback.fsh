#version 150

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Time;

in vec2 texCoord;
out vec4 fragColor;

// ============================================================
// TWEAK CONSTANTS
// ============================================================

// Makes the whole effect weaker/stronger overall.
// Lower than 1.0 = softer max effect.
const float EFFECT_INTENSITY_MULTIPLIER = 0.78;

// Base logical noise pixel size at 1080p.
// Was effectively about 2.0 before; 4.0 is about 2x bigger.
const float BASE_LOGICAL_PIXEL_SIZE = 4.0;

// Dot density scaling.
// Increase if you want more dots at the same intensity.
const float NOISE_CHANCE_MULTIPLIER = 1.15;

// Visual brightness of the white dots.
const float DOT_STRENGTH_MIN = 0.22;
const float DOT_STRENGTH_MAX = 0.68;

// Chromatic aberration strength.
const float ABERRATION_STRENGTH = 0.0042;

// Slight desaturation/wash.
const float WASH_STRENGTH = 0.018;

// ============================================================

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float logicalPixelSize(vec2 screen) {
    float basis = min(screen.x, screen.y);
    float resolutionScale = basis / 1080.0;
    return max(1.0, floor(BASE_LOGICAL_PIXEL_SIZE * resolutionScale + 0.5));
}

float logicalPixelNoise(vec2 tex, vec2 screen, float density, float seed) {
    float pixelSize = logicalPixelSize(screen);

    vec2 logicalPixel = floor((tex * screen) / pixelSize);
    float frame = floor(Time * 24.0);

    float chance = mix(0.0, 0.022 * NOISE_CHANCE_MULTIPLIER, density);
    float n = hash12(logicalPixel + vec2(seed * 19.7, frame * 13.1));

    return step(1.0 - chance, n);
}

float logicalClusterNoise(vec2 tex, vec2 screen, float density, float seed) {
    float pixelSize = logicalPixelSize(screen);

    vec2 logicalPixel = floor((tex * screen) / pixelSize);

    vec2 parent = floor(logicalPixel / 2.0);
    vec2 local = mod(logicalPixel, 2.0);

    float frame = floor(Time * 18.0);

    float chance = mix(0.0, 0.008 * NOISE_CHANCE_MULTIPLIER, density);
    float active = step(1.0 - chance, hash12(parent + vec2(seed * 31.1, frame * 7.3)));

float widthRoll = hash12(parent + vec2(seed * 11.3, frame * 17.7));
float heightRoll = hash12(parent + vec2(seed * 23.9, frame * 29.1));

float width = (widthRoll < 0.55) ? 1.0 : 2.0;
float height = (heightRoll < 0.55) ? 1.0 : 2.0;

float shapeMask = step(local.x, width - 0.5) * step(local.y, height - 0.5);

return active * shapeMask;
}

float radiationMask(vec2 tex, vec2 screen, float density) {
    float mask = 0.0;

    mask = max(mask, logicalPixelNoise(tex, screen, density, 11.0));
    mask = max(mask, logicalPixelNoise(tex, screen, density * 0.75, 29.0));
    mask = max(mask, logicalPixelNoise(tex, screen, density * 0.55, 47.0));

    mask = max(mask, logicalClusterNoise(tex, screen, density, 83.0));
    mask = max(mask, logicalClusterNoise(tex, screen, density * 0.7, 131.0));

    return clamp(mask, 0.0, 1.0);
}

vec2 safeUv(vec2 uv) {
    return clamp(uv, vec2(0.001), vec2(0.999));
}

void main() {
    float intensity = clamp(Intensity, 0.0, 1.0);

    // Linear scaling, then softened overall max strength.
    float effectIntensity = clamp(intensity * EFFECT_INTENSITY_MULTIPLIER, 0.0, 1.0);

    vec2 screen = vec2(textureSize(DiffuseSampler, 0));

    // Linear, not squared.
    float density = effectIntensity;

    vec2 centerOffset = texCoord - vec2(0.5);
    float distFromCenter = length(centerOffset);

    vec2 radialDir = distFromCenter > 0.0001
    ? centerOffset / distFromCenter
    : vec2(0.0, 0.0);

    float edgeFactor = smoothstep(0.08, 0.85, distFromCenter);

    // Also linear now, and slightly weaker.
    float aberrationAmount = ABERRATION_STRENGTH * effectIntensity * edgeFactor;

    vec2 texR = safeUv(texCoord + radialDir * aberrationAmount);
    vec2 texG = safeUv(texCoord);
    vec2 texB = safeUv(texCoord - radialDir * aberrationAmount);

    float maskR = radiationMask(texR, screen, density);
    float maskG = radiationMask(texG, screen, density);
    float maskB = radiationMask(texB, screen, density);

    float dotStrength = mix(DOT_STRENGTH_MIN, DOT_STRENGTH_MAX, effectIntensity);

    float sceneR = texture(DiffuseSampler, texR).r;
    float sceneG = texture(DiffuseSampler, texG).g;
    float sceneB = texture(DiffuseSampler, texB).b;
    float alpha = texture(DiffuseSampler, texCoord).a;

    float outR = mix(sceneR, 1.0, maskR * dotStrength);
    float outG = mix(sceneG, 1.0, maskG * dotStrength);
    float outB = mix(sceneB, 1.0, maskB * dotStrength);

    vec3 color = vec3(outR, outG, outB);

    float wash = effectIntensity * WASH_STRENGTH;
    float luminance = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(color, vec3(luminance), wash);

    fragColor = vec4(color, alpha);
}