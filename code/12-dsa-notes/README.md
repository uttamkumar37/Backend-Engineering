# Code Progression — Topic 12: DSA Maintenance

Companion code for [topics/12-dsa-notes.md](../../topics/12-dsa-notes.md). One clean
implementation per pattern from the concept doc's Section 2 catalog, each verified against a
known correct answer. Run directly: `java <File>.java` (JDK 21 single-file launch).

## Beginner (`01-beginner/`)

- **TwoPointersDemo** — Two Sum on a sorted array (O(n)), and Container With Most Water (moving
  the shorter wall's pointer, since it's always the bottleneck). Confirmed: max area = 49.
- **SlidingWindowDemo** — longest substring without repeating characters, and a fixed-size
  max-sum window. Confirmed: length 3 ("abc"), max window sum 9.

## Intermediate (`02-intermediate/`)

- **BinarySearchOnAnswerDemo** — the sub-pattern the concept doc flags as most often missed:
  binary searching over the space of possible *answers* (not the input array), using a monotonic
  feasibility check. Confirmed: splitting `[7,2,5,10,8]` into 2 pieces minimizing the largest
  piece's sum gives exactly **18**.
- **FastSlowPointerDemo** — Floyd's cycle detection and finding a linked list's middle without
  knowing its length upfront. Confirmed: correctly reports no cycle, middle = 3, then correctly
  detects a cycle after one is introduced.
- **TopKHeapDemo** — kth-largest via a bounded min-heap (O(n log k)). Confirmed: 2nd largest = 5.

## Advanced (`03-advanced/`)

- **DynamicProgrammingDemo** — both solutions lead with the state definition as a comment before
  any code, per the concept doc's "highest-leverage DP skill." Confirmed: edit distance
  "horse"→"ros" = 3; coin change for 11 with `[1,2,5]` = 3 coins.
- **BacktrackingDemo** — permutation generation and N-Queens. Confirmed: 6 permutations of
  `[1,2,3]`; **92 solutions** for 8-Queens — the well-known correct answer for that board size.
- **UnionFindDemo** — path compression + union by rank, applied to "number of provinces."
  Confirmed: 2 provinces for the given adjacency grid; connectivity queries correct both ways.

## How to use this progression

Before running each file, try to name the pattern from the filename alone, then check your
reasoning against the comment at the top of the file — that's the actual skill this topic is
about, not the code itself.
