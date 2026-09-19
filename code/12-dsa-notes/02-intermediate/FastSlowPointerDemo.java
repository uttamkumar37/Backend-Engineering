// Pattern: fast/slow pointers (Floyd's cycle detection). Recognize by "linked list" + "cycle"
// or "find the middle without knowing the length upfront."
public class FastSlowPointerDemo {

    static class Node {
        int value;
        Node next;
        Node(int value) { this.value = value; }
    }

    static boolean hasCycle(Node head) {
        Node slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) return true; // fast necessarily laps slow inside a cycle
        }
        return false;
    }

    static Node middle(Node head) {
        Node slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        return slow; // when fast has gone twice as far, slow is at the midpoint
    }

    public static void main(String[] args) {
        Node a = new Node(1), b = new Node(2), c = new Node(3), d = new Node(4), e = new Node(5);
        a.next = b; b.next = c; c.next = d; d.next = e;

        System.out.println("Acyclic list has cycle: " + hasCycle(a) + " (expected false)");
        System.out.println("Middle of 1->2->3->4->5: " + middle(a).value + " (expected 3)");

        e.next = c; // introduce a cycle: 5 -> back to 3
        System.out.println("After introducing a cycle, has cycle: " + hasCycle(a) + " (expected true)");
    }
}
