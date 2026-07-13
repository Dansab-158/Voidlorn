package net.lobster.voidlorn.worldgen.cell;

public record CellParameters(
        long cellId,
        int centerX,
        int centerZ,
        int centerY,
        int verticalExtent,
        int ruggednessTier
) {}
