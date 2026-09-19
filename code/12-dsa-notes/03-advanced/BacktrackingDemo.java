import java.util.ArrayList;
import java.util.List;

// Pattern: backtracking. Recognize by "generate all possible ..." combined with a
// pruning/validity condition - try a choice, recurse, undo the choice if it doesn't pan out.
public class BacktrackingDemo {

    static List<List<Integer>> permutations(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        backtrackPermutations(nums, new ArrayList<>(), new boolean[nums.length], result);
        return result;
    }

    static void backtrackPermutations(int[] nums, List<Integer> current, boolean[] used, List<List<Integer>> result) {
        if (current.size() == nums.length) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = 0; i < nums.length; i++) {
            if (used[i]) continue;
            used[i] = true;
            current.add(nums[i]);
            backtrackPermutations(nums, current, used, result);   // try
            current.remove(current.size() - 1);                  // undo
            used[i] = false;                                      // undo
        }
    }

    // classic N-Queens count: place queens one row at a time, pruning any column/diagonal conflict
    static int countNQueensSolutions(int n) {
        return solveNQueens(new int[n], 0, n);
    }

    static int solveNQueens(int[] queenColumnPerRow, int row, int n) {
        if (row == n) return 1;
        int solutions = 0;
        for (int col = 0; col < n; col++) {
            if (isValidPlacement(queenColumnPerRow, row, col)) {
                queenColumnPerRow[row] = col;
                solutions += solveNQueens(queenColumnPerRow, row + 1, n);
            }
        }
        return solutions;
    }

    static boolean isValidPlacement(int[] queenColumnPerRow, int row, int col) {
        for (int prevRow = 0; prevRow < row; prevRow++) {
            int prevCol = queenColumnPerRow[prevRow];
            if (prevCol == col) return false;
            if (Math.abs(prevCol - col) == Math.abs(prevRow - row)) return false; // diagonal
        }
        return true;
    }

    public static void main(String[] args) {
        List<List<Integer>> perms = permutations(new int[]{1, 2, 3});
        System.out.println("Permutations of [1,2,3]: " + perms + " (expected 6 permutations)");

        System.out.println("N-Queens solutions for N=8: " + countNQueensSolutions(8) + " (expected 92)");
    }
}
