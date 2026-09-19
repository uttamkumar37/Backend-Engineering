import java.util.PriorityQueue;

// Pattern: heap/priority queue. Recognize by "kth largest/smallest", "top-k", or needing
// repeated access to a min/max under insertion.
public class TopKHeapDemo {

    // A min-heap of size k: after processing all elements, the heap's root is the kth largest.
    // O(n log k) instead of O(n log n) for a full sort - the win grows as n grows relative to k.
    static int kthLargest(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        for (int n : nums) {
            minHeap.offer(n);
            if (minHeap.size() > k) minHeap.poll(); // evict the smallest once we exceed k
        }
        return minHeap.peek();
    }

    public static void main(String[] args) {
        int[] nums = {3, 2, 1, 5, 6, 4};
        int k = 2;
        System.out.println(k + "th largest in " + java.util.Arrays.toString(nums) + ": "
                + kthLargest(nums, k) + " (expected 5)");
    }
}
