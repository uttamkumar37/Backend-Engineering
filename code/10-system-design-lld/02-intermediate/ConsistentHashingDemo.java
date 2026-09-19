import java.security.MessageDigest;
import java.util.SortedMap;
import java.util.TreeMap;

// Naive hash(key) % N remaps almost every key when N changes. Consistent hashing maps both
// nodes and keys onto a ring; adding/removing one node only remaps the keys that were adjacent
// to it - this is the actual mechanism behind graceful rebalancing in distributed caches.
public class ConsistentHashingDemo {

    static long hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            long h = 0;
            for (int i = 0; i < 8; i++) h = (h << 8) | (digest[i] & 0xff);
            return h & Long.MAX_VALUE;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class NaiveModuloRing {
        int nodeCount;
        NaiveModuloRing(int nodeCount) { this.nodeCount = nodeCount; }
        String assign(String key) { return "node-" + (Math.abs(key.hashCode()) % nodeCount); }
    }

    static class ConsistentHashRing {
        private final SortedMap<Long, String> ring = new TreeMap<>();
        private final int virtualNodesPerNode;

        ConsistentHashRing(int virtualNodesPerNode) { this.virtualNodesPerNode = virtualNodesPerNode; }

        void addNode(String node) {
            for (int i = 0; i < virtualNodesPerNode; i++) {
                ring.put(hash(node + "#" + i), node);
            }
        }

        void removeNode(String node) {
            for (int i = 0; i < virtualNodesPerNode; i++) {
                ring.remove(hash(node + "#" + i));
            }
        }

        String assign(String key) {
            long h = hash(key);
            SortedMap<Long, String> tail = ring.tailMap(h);
            long targetHash = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
            return ring.get(targetHash);
        }
    }

    public static void main(String[] args) {
        int keyCount = 10_000;
        String[] keys = new String[keyCount];
        for (int i = 0; i < keyCount; i++) keys[i] = "key-" + i;

        System.out.println("=== Naive hash % N: adding a 5th node to a 4-node cluster ===");
        NaiveModuloRing before4 = new NaiveModuloRing(4);
        NaiveModuloRing after5 = new NaiveModuloRing(5);
        int naiveRemapped = 0;
        for (String key : keys) {
            if (!before4.assign(key).equals(after5.assign(key))) naiveRemapped++;
        }
        System.out.println("Keys remapped: " + naiveRemapped + " / " + keyCount
                + " (" + (naiveRemapped * 100 / keyCount) + "%)");

        System.out.println();
        System.out.println("=== Consistent hashing: adding a 5th node to a 4-node cluster ===");
        ConsistentHashRing ring4 = new ConsistentHashRing(100);
        for (int i = 0; i < 4; i++) ring4.addNode("node-" + i);

        String[] beforeAssignment = new String[keyCount];
        for (int i = 0; i < keyCount; i++) beforeAssignment[i] = ring4.assign(keys[i]);

        ring4.addNode("node-4"); // the 5th node joins the SAME ring

        int consistentRemapped = 0;
        for (int i = 0; i < keyCount; i++) {
            if (!beforeAssignment[i].equals(ring4.assign(keys[i]))) consistentRemapped++;
        }
        System.out.println("Keys remapped: " + consistentRemapped + " / " + keyCount
                + " (" + (consistentRemapped * 100 / keyCount) + "%) - only keys near the new node's position moved");
    }
}
