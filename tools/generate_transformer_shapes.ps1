param(
    [string]$ModelPath = "src/main/resources/assets/skyent/models/block/mv_transformer.json"
)

$model = Get-Content $ModelPath -Raw | ConvertFrom-Json

# Matches assets/skyent/models/block/lv_mv_transformer_scaled_north.json.
$origin = @(8.0, 0.0, 8.0)
$translation = @(0.0, 0.0, 8.0)
$scale = 2.0

$cells = @{}
foreach ($key in @("0,0", "0,1", "1,0", "1,1")) {
    $cells[$key] = New-Object System.Collections.Generic.List[object]
}

$elementIndex = 0
foreach ($element in $model.elements) {
    if ($element.rotation -and [Math]::Abs([double]$element.rotation.angle) -gt 0.000001) {
        Write-Warning "Element $elementIndex has a non-zero rotation and needs generator support."
    }

    $mins = @()
    $maxs = @()
    for ($i = 0; $i -lt 3; $i++) {
        $a = $origin[$i] + ([double]$element.from[$i] - $origin[$i]) * $scale + $translation[$i]
        $b = $origin[$i] + ([double]$element.to[$i] - $origin[$i]) * $scale + $translation[$i]
        $mins += [Math]::Min($a, $b)
        $maxs += [Math]::Max($a, $b)
    }

    foreach ($cellY in @(0, 1)) {
        foreach ($cellZ in @(0, 1)) {
            $clipMinX = [Math]::Max($mins[0], 0.0)
            $clipMaxX = [Math]::Min($maxs[0], 16.0)
            $clipMinY = [Math]::Max($mins[1], 16.0 * $cellY)
            $clipMaxY = [Math]::Min($maxs[1], 16.0 * ($cellY + 1))
            $clipMinZ = [Math]::Max($mins[2], 16.0 * $cellZ)
            $clipMaxZ = [Math]::Min($maxs[2], 16.0 * ($cellZ + 1))

            if (($clipMaxX - $clipMinX -gt 0.000001) -and
                    ($clipMaxY - $clipMinY -gt 0.000001) -and
                    ($clipMaxZ - $clipMinZ -gt 0.000001)) {
                $cells["$cellY,$cellZ"].Add([pscustomobject]@{
                    X1 = $clipMinX
                    Y1 = $clipMinY - 16.0 * $cellY
                    Z1 = $clipMinZ - 16.0 * $cellZ
                    X2 = $clipMaxX
                    Y2 = $clipMaxY - 16.0 * $cellY
                    Z2 = $clipMaxZ - 16.0 * $cellZ
                    Element = $elementIndex
                })
            }
        }
    }

    $elementIndex++
}

function Format-Double([double]$value) {
    if ([Math]::Abs($value - [Math]::Round($value)) -lt 0.000001) {
        return "$([int][Math]::Round($value)).0D"
    }
    return $value.ToString("0.####", [Globalization.CultureInfo]::InvariantCulture) + "D"
}

$names = @{
    "0,0" = "LOWER_FRONT_NORTH_SHAPE"
    "0,1" = "LOWER_REAR_NORTH_SHAPE"
    "1,0" = "UPPER_FRONT_NORTH_SHAPE"
    "1,1" = "UPPER_REAR_NORTH_SHAPE"
}

foreach ($key in @("0,0", "0,1", "1,0", "1,1")) {
    Write-Output "private static final VoxelShape $($names[$key]) = Shapes.or("
    $boxes = $cells[$key]
    for ($i = 0; $i -lt $boxes.Count; $i++) {
        $box = $boxes[$i]
        $coords = @($box.X1, $box.Y1, $box.Z1, $box.X2, $box.Y2, $box.Z2) | ForEach-Object {
            Format-Double $_
        }
        $comma = if ($i -lt $boxes.Count - 1) { "," } else { "" }
        Write-Output "        Block.box($($coords -join ', '))$comma"
    }
    Write-Output ");"
    Write-Output "// boxes $($boxes.Count)"
}
