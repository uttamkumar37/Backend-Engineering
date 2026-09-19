import java.util.HashMap;
import java.util.Map;

// O(1) get AND put requires a HashMap (O(1) lookup) PLUS a doubly linked list (O(1) removal
// from the middle, which a HashMap+singly-linked-list or HashMap+ArrayList cannot give you).
public class LruCacheDemo {

    static class Node {
        int key, value;
        Node prev, next;
        Node(int key, int value) { this.key = key; this.value = value; }
    }

    static class LruCache {
        private final int capacity;
        private final Map<Integer, Node> map = new HashMap<>();
        private final Node head = new Node(-1, -1); // dummy most-recently-used sentinel
        private final Node tail = new Node(-1, -1); // dummy least-recently-used sentinel

        LruCache(int capacity) {
            this.capacity = capacity;
            head.next = tail;
            tail.prev = head;
        }

        int get(int key) {
            Node node = map.get(key);
            if (node == null) return -1;
            moveToFront(node);
            return node.value;
        }

        void put(int key, int value) {
            Node existing = map.get(key);
            if (existing != null) {
                existing.value = value;
                moveToFront(existing);
                return;
            }
            if (map.size() == capacity) {
                Node lru = tail.prev;
                remove(lru);
                map.remove(lru.key);
            }
            Node fresh = new Node(key, value);
            map.put(key, fresh);
            addToFront(fresh);
        }

        private void moveToFront(Node node) {
            remove(node);
            addToFront(node);
        }

        private void remove(Node node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }

        private void addToFront(Node node) {
            node.next = head.next;
            node.prev = head;
            head.next.prev = node;
            head.next = node;
        }
    }

    public static void main(String[] args) {
        LruCache cache = new LruCache(3);
        cache.put(1, 100);
        cache.put(2, 200);
        cache.put(3, 300);
        System.out.println("get(1) = " + cache.get(1) + " (moves 1 to most-recently-used)");

        cache.put(4, 400); // capacity exceeded - evicts the LEAST recently used, which is now 2
        System.out.println("get(2) after evicting = " + cache.get(2) + " (expected -1, evicted)");
        System.out.println("get(1) = " + cache.get(1) + " (expected 100, survived because it was used recently)");
        System.out.println("get(3) = " + cache.get(3) + " (expected 300, still present)");
        System.out.println("get(4) = " + cache.get(4) + " (expected 400, just inserted)");
    }
}
