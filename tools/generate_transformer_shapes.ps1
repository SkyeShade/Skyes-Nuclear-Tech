param(
    [string]$ModelPath = "src/main/resources/assets/skyent/models/block/mv_transformer.json",
    [double[]]$Origin = @(8.0, 0.0, 8.0),
    [double[]]$Translation = @(0.0, 0.0, 8.0),
    [double]$Scale = 2.0,
    [int]$SizeX = 1,
    [int]$SizeY = 2,
    [int]$SizeZ = 2,
    [string]$PackageName = "",
    [string]$ClassName = "",
    [string]$HeaderComment = ""
)

$model = Get-Content $ModelPath -Raw | ConvertFrom-Json

$cells = @{}
for ($cellY = 0; $cellY -lt $SizeY; $cellY++) {
    for ($cellX = 0; $cellX -lt $SizeX; $cellX++) {
        for ($cellZ = 0; $cellZ -lt $SizeZ; $cellZ++) {
            $cells["$cellX,$cellY,$cellZ"] = New-Object System.Collections.Generic.List[object]
        }
    }
}

$elementIndex = 0
foreach ($element in $model.elements) {
    if ($element.rotation -and [Math]::Abs([double]$element.rotation.angle) -gt 0.000001) {
        Write-Warning "Element $elementIndex has a non-zero rotation and needs generator support."
    }

    $mins = @()
    $maxs = @()
    for ($i = 0; $i -lt 3; $i++) {
        $a = $Origin[$i] + ([double]$element.from[$i] - $Origin[$i]) * $Scale + $Translation[$i]
        $b = $Origin[$i] + ([double]$element.to[$i] - $Origin[$i]) * $Scale + $Translation[$i]
        $mins += [Math]::Min($a, $b)
        $maxs += [Math]::Max($a, $b)
    }

    for ($cellY = 0; $cellY -lt $SizeY; $cellY++) {
        for ($cellX = 0; $cellX -lt $SizeX; $cellX++) {
            for ($cellZ = 0; $cellZ -lt $SizeZ; $cellZ++) {
                $clipMinX = [Math]::Max($mins[0], 16.0 * $cellX)
                $clipMaxX = [Math]::Min($maxs[0], 16.0 * ($cellX + 1))
                $clipMinY = [Math]::Max($mins[1], 16.0 * $cellY)
                $clipMaxY = [Math]::Min($maxs[1], 16.0 * ($cellY + 1))
                $clipMinZ = [Math]::Max($mins[2], 16.0 * $cellZ)
                $clipMaxZ = [Math]::Min($maxs[2], 16.0 * ($cellZ + 1))

                if (($clipMaxX - $clipMinX -gt 0.000001) -and
                        ($clipMaxY - $clipMinY -gt 0.000001) -and
                        ($clipMaxZ - $clipMinZ -gt 0.000001)) {
                    $cells["$cellX,$cellY,$cellZ"].Add([pscustomobject]@{
                        X1 = $clipMinX - 16.0 * $cellX
                        Y1 = $clipMinY - 16.0 * $cellY
                        Z1 = $clipMinZ - 16.0 * $cellZ
                        X2 = $clipMaxX - 16.0 * $cellX
                        Y2 = $clipMaxY - 16.0 * $cellY
                        Z2 = $clipMaxZ - 16.0 * $cellZ
                        Element = $elementIndex
                    })
                }
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

function Write-ShapeInitializer([System.Collections.Generic.List[object]]$boxes) {
    if ($boxes.Count -eq 0) {
        Write-Output "            Shapes.empty()"
        return
    }
    if ($boxes.Count -eq 1) {
        $box = $boxes[0]
        $coords = @($box.X1, $box.Y1, $box.Z1, $box.X2, $box.Y2, $box.Z2) | ForEach-Object {
            Format-Double $_
        }
        Write-Output "            Block.box($($coords -join ', '))"
        return
    }
    Write-Output "            Shapes.or("
    for ($i = 0; $i -lt $boxes.Count; $i++) {
        $box = $boxes[$i]
        $coords = @($box.X1, $box.Y1, $box.Z1, $box.X2, $box.Y2, $box.Z2) | ForEach-Object {
            Format-Double $_
        }
        $comma = if ($i -lt $boxes.Count - 1) { "," } else { "" }
        Write-Output "                    Block.box($($coords -join ', '))$comma"
    }
    Write-Output "            )"
}

if ($ClassName -ne "") {
    if ($PackageName -ne "") {
        Write-Output "package $PackageName;"
        Write-Output ""
    }
    Write-Output "import net.minecraft.core.Direction;"
    Write-Output "import net.minecraft.world.level.block.Block;"
    Write-Output "import net.minecraft.world.phys.shapes.Shapes;"
    Write-Output "import net.minecraft.world.phys.shapes.VoxelShape;"
    Write-Output ""
    if ($HeaderComment -ne "") {
        Write-Output $HeaderComment
    }
    Write-Output "public final class $ClassName {"
    Write-Output "    private static final int SIZE_X = $SizeX;"
    Write-Output "    private static final int SIZE_Y = $SizeY;"
    Write-Output "    private static final int SIZE_Z = $SizeZ;"
    Write-Output ""
    Write-Output "    private static final VoxelShape[] NORTH_SHAPES = new VoxelShape[] {"
    for ($cellY = 0; $cellY -lt $SizeY; $cellY++) {
        for ($cellX = 0; $cellX -lt $SizeX; $cellX++) {
            for ($cellZ = 0; $cellZ -lt $SizeZ; $cellZ++) {
                $key = "$cellX,$cellY,$cellZ"
                Write-Output "            // local x=$cellX, y=$cellY, z=$cellZ, boxes=$($cells[$key].Count)"
                Write-ShapeInitializer $cells[$key]
                $isLast = ($cellY -eq $SizeY - 1) -and ($cellX -eq $SizeX - 1) -and ($cellZ -eq $SizeZ - 1)
                if (-not $isLast) {
                    Write-Output "            ,"
                }
            }
        }
    }
    Write-Output "    };"
    Write-Output ""
    Write-Output "    private static final VoxelShape[][] LOCAL_SHAPES = new VoxelShape[NORTH_SHAPES.length][];"
    Write-Output ""
    Write-Output "    static {"
    Write-Output "        for (int i = 0; i < NORTH_SHAPES.length; i++) {"
    Write-Output "            LOCAL_SHAPES[i] = shapesByFacing(NORTH_SHAPES[i]);"
    Write-Output "        }"
    Write-Output "    }"
    Write-Output ""
    Write-Output "    private $ClassName() {"
    Write-Output "    }"
    Write-Output ""
    Write-Output "    public static VoxelShape shapeForLocal(int x, int y, int z, Direction facing) {"
    Write-Output "        int clampedX = Math.max(0, Math.min(SIZE_X - 1, x));"
    Write-Output "        int clampedY = Math.max(0, Math.min(SIZE_Y - 1, y));"
    Write-Output "        int clampedZ = Math.max(0, Math.min(SIZE_Z - 1, z));"
    Write-Output "        int index = (clampedY * SIZE_X + clampedX) * SIZE_Z + clampedZ;"
    Write-Output "        return shapeForFacing(LOCAL_SHAPES[index], facing);"
    Write-Output "    }"
    Write-Output ""
    Write-Output "    private static VoxelShape[] shapesByFacing(VoxelShape northShape) {"
    Write-Output "        return new VoxelShape[] {"
    Write-Output "                northShape,"
    Write-Output "                rotateShape(northShape, Direction.EAST),"
    Write-Output "                rotateShape(northShape, Direction.SOUTH),"
    Write-Output "                rotateShape(northShape, Direction.WEST)"
    Write-Output "        };"
    Write-Output "    }"
    Write-Output ""
    Write-Output "    private static VoxelShape shapeForFacing(VoxelShape[] shapes, Direction facing) {"
    Write-Output "        return switch (facing) {"
    Write-Output "            case EAST -> shapes[1];"
    Write-Output "            case SOUTH -> shapes[2];"
    Write-Output "            case WEST -> shapes[3];"
    Write-Output "            default -> shapes[0];"
    Write-Output "        };"
    Write-Output "    }"
    Write-Output ""
    Write-Output "    private static VoxelShape rotateShape(VoxelShape shape, Direction facing) {"
    Write-Output "        VoxelShape[] rotated = {Shapes.empty()};"
    Write-Output "        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {"
    Write-Output "            VoxelShape box = switch (facing) {"
    Write-Output "                case EAST -> Shapes.box(1.0D - maxZ, minY, minX, 1.0D - minZ, maxY, maxX);"
    Write-Output "                case SOUTH -> Shapes.box(1.0D - maxX, minY, 1.0D - maxZ, 1.0D - minX, maxY, 1.0D - minZ);"
    Write-Output "                case WEST -> Shapes.box(minZ, minY, 1.0D - maxX, maxZ, maxY, 1.0D - minX);"
    Write-Output "                default -> Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);"
    Write-Output "            };"
    Write-Output "            rotated[0] = Shapes.or(rotated[0], box);"
    Write-Output "        });"
    Write-Output "        return rotated[0];"
    Write-Output "    }"
    Write-Output "}"
} else {
    for ($cellY = 0; $cellY -lt $SizeY; $cellY++) {
        for ($cellX = 0; $cellX -lt $SizeX; $cellX++) {
            for ($cellZ = 0; $cellZ -lt $SizeZ; $cellZ++) {
                $key = "$cellX,$cellY,$cellZ"
                Write-Output "// local x=$cellX, y=$cellY, z=$cellZ, boxes=$($cells[$key].Count)"
                Write-ShapeInitializer $cells[$key]
            }
        }
    }
}
