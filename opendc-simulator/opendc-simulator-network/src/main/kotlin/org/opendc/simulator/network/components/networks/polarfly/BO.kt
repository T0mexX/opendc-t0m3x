package org.opendc.simulator.network.components.networks.polarfly

import org.opendc.simulator.network.components.networks.polarfly.FiniteField.ERGraph
import kotlin.math.sqrt


private class FiniteField(private val q: Int) {
    init {
        require(q.isPrime())
    }
    private fun Int.isPrime(): Boolean {
        if (this < 2) return false
        val sqrt = sqrt(this.toDouble()).toInt()
        return (2..sqrt).none { this % it == 0 }
    }

    private fun mod(x: Int): Int = ((x % q) + q) % q

    data class ProjectivePoint(val coords: Triple<Int, Int, Int>)

    private fun normalize(a1: Int, a2: Int, a3: Int): ProjectivePoint {
        val coords = listOf(a1, a2, a3).map { mod(it) }
        val firstNonZero = coords.indexOfFirst { it != 0 }
        if (firstNonZero == -1) {
            throw IllegalArgumentException("Zero vector is not a projective point")
        }
        val inv = modInverse(coords[firstNonZero], q)
        val normalized = coords.map { mod(it * inv) }
        return ProjectivePoint(Triple(normalized[0], normalized[1], normalized[2]))
    }


    // Modular inverse using Extended Euclidean algorithm
    private fun modInverse(a: Int, m: Int): Int {
        var m0 = m
        var y = 0
        var x = 1
        var aVar = a

        if (m == 1) return 0
        while (aVar > 1) {
            val q = aVar / m0
            var t = m0

            m0 = aVar % m0
            aVar = t
            t = y

            y = x - q * y
            x = t
        }
        if (x < 0) x += m
        return x
    }

    private fun generateProjectivePoints(): Set<ProjectivePoint> {
        val points = mutableSetOf<ProjectivePoint>()
        for (a1 in 0 until q) {
            for (a2 in 0 until q) {
                for (a3 in 0 until q) {
                    if (a1 == 0 && a2 == 0 && a3 == 0) continue
                    try {
                        val p = normalize(a1, a2, a3)
                        points.add(p)
                    } catch (_: IllegalArgumentException) {
                        // skip zero vector
                    }
                }
            }
        }
        return points
    }

    private fun isIncident(point: ProjectivePoint, line: ProjectivePoint): Boolean {
        val (x1, x2, x3) = point.coords
        val (b1, b2, b3) = line.coords
        val sum = mod(b1 * x1 + b2 * x2 + b3 * x3)
        return sum == 0
    }

    data class Vertex(val id: Int, val isPoint: Boolean, val repr: ProjectivePoint)

    inner class BipartiteGraph(val q: Int) {
        val points = generateProjectivePoints()
        val lines = generateProjectivePoints() // lines have same form

        val pointList = points.toList()
        val lineList = lines.toList()

        // Vertex IDs: 0..points.size-1 for points, points.size..points.size+lines.size-1 for lines
        val vertices = mutableListOf<Vertex>()
        val adjacency = mutableMapOf<Int, MutableList<Int>>() // id -> list of adjacent ids

        init {
            pointList.forEachIndexed { i, p -> vertices.add(Vertex(i, true, p)) }
            lineList.forEachIndexed { i, l -> vertices.add(Vertex(points.size + i, false, l)) }

            for ((i, p) in pointList.withIndex()) {
                for ((j, l) in lineList.withIndex()) {
                    if (isIncident(p, l)) {
                        adjacency.getOrPut(i) { mutableListOf() }.add(points.size + j)
                        adjacency.getOrPut(points.size + j) { mutableListOf() }.add(i)
                    }
                }
            }
        }
    }

    class ERGraph(private val bipartiteGraph: BipartiteGraph) {
        val q = bipartiteGraph.q
        val n = bipartiteGraph.points.size

        // Map from point to line vertex index
        val pointToLineVertexId = mutableMapOf<Int, Int>()
        // Construct mapping from point's coords to line vertex id
        private val lineCoordToId = mutableMapOf<ProjectivePoint, Int>()

        private val vertices = mutableListOf<Vertex>() // ERq vertices
        val adjacency = mutableMapOf<Int, MutableSet<Int>>() // ERq adjacency

        fun isQuadric(vertex: Vertex, q: Int): Boolean {
            val (a1, a2, a3) = vertex.repr.coords
            // Use modulo arithmetic (adjust negatives)
            fun sq(x: Int) = ((x % q + q) % q).let { (it * it) % q }
            val sum = (sq(a1) + sq(a2) + sq(a3)) % q
            return sum == 0
        }

        fun classifyVertices(): Triple<Set<Int>, Set<Int>, Set<Int>> {
            val W = vertices.filter { isQuadric(it, q) }.map { it.id }.toSet()
            val adjacentToW = mutableSetOf<Int>()
            for (w in W) {
                adjacency[w]?.let { adjacentToW.addAll(it) }
            }
            val V1 = adjacentToW - W
            val V2 = vertices.map { it.id }.toSet() - W - V1
            return Triple(W, V1, V2)
        }

        fun testProperties() {
            val (W, V1, V2) = classifyVertices()
            println("W size: ${W.size}, V1 size: ${V1.size}, V2 size: ${V2.size}")

            // Property 1: No two vertices in W are adjacent.
            for (w1 in W) {
                for (w2 in adjacency[w1] ?: emptySet()) {
                    require(w2 !in W) { "Property 1 violated: W vertex $w1 adjacent to W vertex $w2" }
                }
            }
            println("Property 1 passed: No two W vertices are adjacent.")

            // Each W vertex adjacent to exactly q vertices in V1
            for (w in W) {
                val adj = adjacency[w] ?: emptySet()
                val countInV1 = adj.count { it in V1 }
                require(countInV1 == q) {
                    "Property 1 violated: W vertex $w adjacent to $countInV1 vertices in V1 (expected $q)"
                }
            }
            println("Property 1 passed: Each W vertex adjacent to exactly q vertices in V1.")

            // Property 2: Each V1 vertex adjacent to exactly 2 vertices in W
            // and vertices each in V1 and V2 as described.
            for (v1 in V1) {
                val adj = adjacency[v1] ?: emptySet()
                val countW = adj.count { it in W }
                val countV1 = adj.count { it in V1 }
                val countV2 = adj.count { it in V2 }
                require(countW == 2) {
                    "Property 2 violated: V1 vertex $v1 adjacent to $countW vertices in W (expected 2)"
                }
                require(countV1 == (q - 1) / 2) {
                    "Property 2 violated: V1 vertex $v1 adjacent to $countV1 vertices in V1 (expected ${q - 1})"
                }
                require(countV2 == (q - 1) / 2) {
                    "Property 2 violated: V1 vertex $v1 adjacent to $countV2 vertices in V2 (expected ${q + 1})"
                }
            }
            println("Property 2 passed.")

            // Property 3: Each V2 vertex adjacent to exactly q vertices each in V1 and V2
            for (v2 in V2) {
                val adj = adjacency[v2] ?: emptySet()
                val countV1 = adj.count { it in V1 }
                val countV2 = adj.count { it in V2 }
                require(countV1 == (q + 1) / 2) {
                    "Property 3 violated: V2 vertex $v2 adjacent to $countV1 vertices in V1 (expected $q)"
                }
                require(countV2 == (q + 1) / 2) {
                    "Property 3 violated: V2 vertex $v2 adjacent to $countV2 vertices in V2 (expected $q)"
                }
            }
            println("Property 3 passed.")

            // Property 4: Exactly one path of length 2 between every vertex pair
            val allVertices = vertices.map { it.id }
            val pairs = allVertices.size * (allVertices.size - 1)
            var cnt = 0
            for (u in allVertices) {
                for (v in allVertices) {
                    if (u == v) continue
                    val neighborsU = (adjacency[u] ?: emptySet()).let { set -> if (isQuadric(vertices.find { it.id == u }!!, q)) set + u else set }
                    val neighborsV = (adjacency[v] ?: emptySet()).let { set -> if (isQuadric(vertices.find { it.id == v }!!, q)) set + v else set }
                    val commonNeighbors = neighborsU.intersect(neighborsV)
                    if (commonNeighbors.size == 1) cnt++
                }
            }
            require(cnt == pairs) {
                "Property 4 violated (Passed $cnt out of $pairs)"
            }
            println("Property 4 passed.")

            // Property 5: Edges incident with quadric vertices have no triangles
            // Edges between non-quadric vertices participate in exactly one triangle
            for ((u, adjU) in adjacency) {
                for (v in adjU) {
                    if (u > v) continue // check each edge once
                    val uIsQuad = u in W
                    val vIsQuad = v in W
                    val neighborsU = adjacency[u] ?: emptySet()
                    val neighborsV = adjacency[v] ?: emptySet()
                    val commonNeighbors = neighborsU.intersect(neighborsV)

                    if (uIsQuad || vIsQuad) {
                        require(commonNeighbors.isEmpty()) {
                            "Property 5 violated: Edge ($u, $v) incident to quadric vertex participates in triangle(s)"
                        }
                    } else {
                        require(commonNeighbors.size == 1) {
                            "Property 5 violated: Edge ($u, $v) between non-quadric vertices participates in ${commonNeighbors.size} triangles (expected 1)"
                        }
                    }
                }
            }
            println("Property 5 passed.")
        }

        init {
            // Map lines coords to their vertex ids
            for (v in bipartiteGraph.vertices) {
                if (!v.isPoint) {
                    lineCoordToId[v.repr] = v.id
                }
            }
            // For each point, find its dual line
            for (v in bipartiteGraph.vertices) {
                if (v.isPoint) {
                    val dualLineId = lineCoordToId[v.repr] ?: error("Dual line not found")
                    pointToLineVertexId[v.id] = dualLineId
                }
            }

            // Gluing: for each point v, glue v and pointToLineVertexId[v]
            // This means one vertex in ERq represents this pair

            val gluedIdMap = mutableMapOf<Int, Int>() // original vertex id -> new glued vertex id
            var currentId = 0

            for (v in bipartiteGraph.vertices) {
                if (v.isPoint) {
                    val lineId = pointToLineVertexId[v.id]!!
                    if (!gluedIdMap.containsKey(v.id) && !gluedIdMap.containsKey(lineId)) {
                        // Create new ERq vertex for glued pair
                        gluedIdMap[v.id] = currentId
                        gluedIdMap[lineId] = currentId

                        // Add vertex representing this pair
                        vertices.add(Vertex(currentId, true, v.repr)) // mark as point (or just glued vertex)
                        currentId++
                    }
                }
            }

            // Handle any vertices not glued (e.g., if line vertex not dual to a point) - none in theory

            // Build adjacency for ERq vertices by merging adjacency of glued vertices
            for (v in bipartiteGraph.vertices) {
                val erId = gluedIdMap[v.id] ?: continue
                val neighbors = bipartiteGraph.adjacency[v.id] ?: emptyList()
                val erNeighbors = neighbors.mapNotNull { gluedIdMap[it] }.filter { it != erId }.toSet()

                adjacency.getOrPut(erId) { mutableSetOf() }.addAll(erNeighbors)
            }
        }
    }

    private fun isQuadricVertex(p: ProjectivePoint): Boolean {
        val (a1, a2, a3) = p.coords
        return mod(a1 * a1 + a2 * a2 + a3 * a3) == 0
    }


    fun getERGraph(): ERGraph {
        val q = 3
        val bipartite = BipartiteGraph(q)
        println("B(q) has ${bipartite.vertices.size} vertices")
        println("Example adjacency: ${bipartite.adjacency.entries.first()}")

        return ERGraph(bipartite)
    }



    fun isQuadric(vertex: Vertex, q: Int): Boolean {
        val (a1, a2, a3) = vertex.repr.coords
        // Use modulo arithmetic (adjust negatives)
        fun sq(x: Int) = ((x % q + q) % q).let { (it * it) % q }
        val sum = (sq(a1) + sq(a2) + sq(a3)) % q
        return sum == 0
    }


}

internal data class ERSpecs(
    val W: Set<Int>,
    val V1: Set<Int>,
    val V2: Set<Int>,
    val adjacency: Map<Int, Set<Int>>
) {
    companion object {
        operator fun invoke(q: Int): ERSpecs {
            val graph = FiniteField(q = q).getERGraph()
            val (W, V1, V2) = graph.classifyVertices()
            return ERSpecs(
                W,
                V1,
                V2,
                graph.adjacency
            )
        }
    }
}

//private fun main() {
//
//    val bo = FiniteField(3).getERGraph()
//    bo.testProperties()
//}


