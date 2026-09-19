// Pattern: Union-Find (Disjoint Set Union). Recognize by "groups that merge over time" or
// "are these two elements connected," especially when queried repeatedly.
public class UnionFindDemo {

    static class DisjointSet {
        final int[] parent;
        final int[] rank;

        DisjointSet(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }

        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]); // path compression
            return parent[x];
        }

        void union(int a, int b) {
            int rootA = find(a), rootB = find(b);
            if (rootA == rootB) return;
            // union by rank: attach the shorter tree under the taller one
            if (rank[rootA] < rank[rootB]) { parent[rootA] = rootB; }
            else if (rank[rootA] > rank[rootB]) { parent[rootB] = rootA; }
            else { parent[rootB] = rootA; rank[rootA]++; }
        }

        boolean connected(int a, int b) {
            return find(a) == find(b);
        }
    }

    // "number of provinces": given a grid of direct connections, count connected components
    static int countProvinces(int[][] isConnected) {
        int n = isConnected.length;
        DisjointSet dsu = new DisjointSet(n);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (isConnected[i][j] == 1) dsu.union(i, j);
            }
        }
        long provinces = 0;
        for (int i = 0; i < n; i++) if (dsu.find(i) == i) provinces++;
        return (int) provinces;
    }

    public static void main(String[] args) {
        int[][] isConnected = {
                {1, 1, 0},
                {1, 1, 0},
                {0, 0, 1}
        };
        System.out.println("Number of provinces: " + countProvinces(isConnected) + " (expected 2)");

        DisjointSet dsu = new DisjointSet(5);
        dsu.union(0, 1);
        dsu.union(1, 2);
        System.out.println("0 and 2 connected: " + dsu.connected(0, 2) + " (expected true)");
        System.out.println("0 and 3 connected: " + dsu.connected(0, 3) + " (expected false)");
    }
}
