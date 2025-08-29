package ai.koog.agents.ext.tool.edit.diff

/**
 * Implementation of Myers' diff algorithm.
 *
 * This algorithm is based on Eugene Myers' paper "An O(ND) Difference Algorithm and Its Variations"
 * and is known for its efficiency in finding the shortest edit script between two sequences.
 *
 * @param T The type of elements being compared
 */
internal class MyersDiffAlgorithm<T> : DiffAlgorithm<T> {
    companion object {
        val forStrings: MyersDiffAlgorithm<String> = MyersDiffAlgorithm<String>()
    }

    override val id: String = "myers"

    override fun diff(source: List<T>, target: List<T>): Diff<T> {
        // If both sequences are identical, return a list of KEEP operations
        if (source == target) {
            return Diff(source.map { DiffOperation(DiffOperation.Type.KEEP, it) })
        }

        // Handle empty inputs
        if (source.isEmpty()) {
            return Diff(target.map { DiffOperation(DiffOperation.Type.INSERT, it) })
        }
        if (target.isEmpty()) {
            return Diff(source.map { DiffOperation(DiffOperation.Type.DELETE, it) })
        }

        val n = source.size
        val m = target.size
        val max = n + m
        val trace = mutableListOf<Map<Int, Int>>()

        // V maps diagonal k to the farthest x reached at edit distance d
        var v = mutableMapOf<Int, Int>().also { it[1] = 0 }

        // Find the shortest edit script
        var d = 0
        outer@ for (d0 in 0..max) {
            d = d0
            val currV = mutableMapOf<Int, Int>()
            for (k in -d..d step 2) {
                // Choose move: down (insert) or right (delete)
                val xStart = if (k == -d || (k != d && (v[k - 1] ?: -1) < (v[k + 1] ?: -1))) {
                    v[k + 1] ?: 0
                } else {
                    (v[k - 1] ?: 0) + 1
                }
                var x = xStart
                var y = x - k

                // "Snake" down the diagonal (matches)
                while (x < n && y < m && source[x] == target[y]) {
                    x++; y++
                }
                currV[k] = x

                // If we've reached the end, stop
                if (x >= n && y >= m) {
                    trace.add(currV)
                    break@outer
                }
            }
            trace.add(currV)
            v = currV
        }

        // Reconstruct the edit script
        return Diff(backtrack(source, target, trace, d))
    }

    /**
     * Backtrack through the trace to reconstruct the edit script.
     */
    private fun backtrack(source: List<T>, target: List<T>, trace: List<Map<Int, Int>>, d: Int): List<DiffOperation<T>> {
        val result = mutableListOf<DiffOperation<T>>()
        var x = source.size
        var y = target.size

        // Process each edit distance level
        for (editDistance in d downTo 0) {
            if (editDistance == 0) break

            val vPrev = trace[editDistance - 1]
            val k = x - y

            // Determine if we moved horizontally (delete) or vertically (insert)
            val prevK = if (k == -editDistance || (k != editDistance && (vPrev[k - 1] ?: -1) < (vPrev[k + 1] ?: -1))) {
                // Moved vertically (insert)
                k + 1
            } else {
                // Moved horizontally (delete)
                k - 1
            }

            // Calculate previous position
            val prevX = vPrev[prevK] ?: 0
            val prevY = prevX - prevK

            // Process diagonal moves (matches) between (prevX,prevY) and (x,y)
            while (x > prevX && y > prevY) {
                result.add(0, DiffOperation(DiffOperation.Type.KEEP, source[x - 1]))
                x--
                y--
            }

            // Process the edit operation
            if (x > prevX) {
                // Horizontal move (delete)
                result.add(0, DiffOperation(DiffOperation.Type.DELETE, source[x - 1]))
                x--
            } else if (y > prevY) {
                // Vertical move (insert)
                result.add(0, DiffOperation(DiffOperation.Type.INSERT, target[y - 1]))
                y--
            }
        }

        // Process any remaining diagonal moves at the beginning
        while (x > 0 && y > 0) {
            result.add(0, DiffOperation(DiffOperation.Type.KEEP, source[x - 1]))
            x--
            y--
        }

        // Process any remaining horizontal moves (deletes)
        while (x > 0) {
            result.add(0, DiffOperation(DiffOperation.Type.DELETE, source[x - 1]))
            x--
        }

        // Process any remaining vertical moves (inserts)
        while (y > 0) {
            result.add(0, DiffOperation(DiffOperation.Type.INSERT, target[y - 1]))
            y--
        }

        return result
    }
}
