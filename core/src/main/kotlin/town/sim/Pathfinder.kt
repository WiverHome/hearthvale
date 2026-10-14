package town.sim

/**
 * A* по сетке, 8 направлений, без срезания углов. Цена входа в клетку = cost[клетка] (0 = непроходимо),
 * по диагонали x14/10. Всё на целых и с фиксированным порядком соседей, поэтому результат детерминирован.
 */
class Pathfinder(private val w: Int, private val h: Int, private val cost: IntArray, private val minCost: Int) {
    private val n = w * h
    private val g = IntArray(n)
    private val parent = IntArray(n)
    private val seen = IntArray(n)    // метка поиска, в котором клетка открыта
    private val closed = IntArray(n)  // метка поиска, в котором клетка закрыта
    private var stamp = 0

    // двоичная куча с ленивым удалением: (f, клетка)
    private var heapF = IntArray(n)
    private var heapNode = IntArray(n)
    private var heapSize = 0

    /** Сколько клеток раскрыл последний поиск (для статистики) */
    var lastExpanded = 0
        private set

    private val dx = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
    private val dy = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

    fun find(start: Int, goal: Int): IntArray? {
        stamp++
        heapSize = 0
        lastExpanded = 0
        val gx = goal % w
        val gy = goal / w
        g[start] = 0
        seen[start] = stamp
        push(heuristic(start % w, start / w, gx, gy), start)
        while (heapSize > 0) {
            val cur = pop()
            if (closed[cur] == stamp) continue
            closed[cur] = stamp
            lastExpanded++
            if (cur == goal) return buildPath(start, goal)
            val cx = cur % w
            val cy = cur / w
            for (d in 0 until 8) {
                val nx = cx + dx[d]
                val ny = cy + dy[d]
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val nb = ny * w + nx
                val c = cost[nb]
                if (c == 0 || closed[nb] == stamp) continue
                var step = c
                if (d >= 4) {
                    // диагональ только если обе соседние прямые клетки проходимы
                    if (cost[cy * w + nx] == 0 || cost[ny * w + cx] == 0) continue
                    step = c * 14 / 10
                }
                val ng = g[cur] + step
                if (seen[nb] != stamp || ng < g[nb]) {
                    seen[nb] = stamp
                    g[nb] = ng
                    parent[nb] = cur
                    push(ng + heuristic(nx, ny, gx, gy), nb)
                }
            }
        }
        return null
    }

    private fun heuristic(x: Int, y: Int, gx: Int, gy: Int): Int {
        val ax = if (x > gx) x - gx else gx - x
        val ay = if (y > gy) y - gy else gy - y
        val mn = if (ax < ay) ax else ay
        val mx = if (ax < ay) ay else ax
        return minCost * (10 * (mx - mn) + 14 * mn) / 10
    }

    private fun buildPath(start: Int, goal: Int): IntArray {
        var len = 0
        var c = goal
        while (c != start) { len++; c = parent[c] }
        val path = IntArray(len)
        c = goal
        for (i in len - 1 downTo 0) { path[i] = c; c = parent[c] }
        return path
    }

    private fun push(f: Int, node: Int) {
        if (heapSize == heapF.size) {
            heapF = heapF.copyOf(heapSize * 2)
            heapNode = heapNode.copyOf(heapSize * 2)
        }
        var i = heapSize++
        while (i > 0) {
            val p = (i - 1) shr 1
            if (heapF[p] <= f) break
            heapF[i] = heapF[p]; heapNode[i] = heapNode[p]
            i = p
        }
        heapF[i] = f; heapNode[i] = node
    }

    private fun pop(): Int {
        val top = heapNode[0]
        heapSize--
        if (heapSize > 0) {
            val f = heapF[heapSize]
            val node = heapNode[heapSize]
            var i = 0
            while (true) {
                var c = 2 * i + 1
                if (c >= heapSize) break
                if (c + 1 < heapSize && heapF[c + 1] < heapF[c]) c++
                if (heapF[c] >= f) break
                heapF[i] = heapF[c]; heapNode[i] = heapNode[c]
                i = c
            }
            heapF[i] = f; heapNode[i] = node
        }
        return top
    }
}
