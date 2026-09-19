import java.util.*;

// The actual hard part of a Splitwise-style expense-sharing system isn't the CRUD entities -
// it's settlement: given N people who owe each other money pairwise, compute the MINIMUM number
// of transactions that settles everyone up. Greedy net-balance matching does this well in
// practice: net every person's balance, then repeatedly match the biggest creditor with the
// biggest debtor.
public class DebtSimplificationDemo {

    record Transaction(String from, String to, double amount) {}

    static List<Transaction> simplify(Map<String, Double> netBalances) {
        // positive balance = is owed money (creditor); negative = owes money (debtor)
        PriorityQueue<Map.Entry<String, Double>> creditors =
                new PriorityQueue<>((a, b) -> Double.compare(b.getValue(), a.getValue()));
        PriorityQueue<Map.Entry<String, Double>> debtors =
                new PriorityQueue<>((a, b) -> Double.compare(a.getValue(), b.getValue()));

        for (var entry : netBalances.entrySet()) {
            if (entry.getValue() > 0.01) creditors.add(entry);
            else if (entry.getValue() < -0.01) debtors.add(entry);
        }

        List<Transaction> transactions = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            var creditor = creditors.poll();
            var debtor = debtors.poll();

            double settled = Math.min(creditor.getValue(), -debtor.getValue());
            transactions.add(new Transaction(debtor.getKey(), creditor.getKey(), settled));

            double remainingCredit = creditor.getValue() - settled;
            double remainingDebt = debtor.getValue() + settled;

            if (remainingCredit > 0.01) creditors.add(Map.entry(creditor.getKey(), remainingCredit));
            if (remainingDebt < -0.01) debtors.add(Map.entry(debtor.getKey(), remainingDebt));
        }
        return transactions;
    }

    public static void main(String[] args) {
        // raw pairwise debts BEFORE simplification (what a naive implementation would settle
        // with one transaction per original expense - far more than necessary):
        //   Asha paid for Ravi ($20), Ravi paid for Meera ($30), Meera paid for Asha ($10)
        //   -> net balances:
        Map<String, Double> netBalances = new LinkedHashMap<>();
        netBalances.put("Asha", -20.0 + 10.0);   // owes 20, is owed 10 -> net -10 (owes 10)
        netBalances.put("Ravi", 20.0 - 30.0);    // is owed 20, owes 30 -> net -10 (owes 10)
        netBalances.put("Meera", 30.0 - 10.0);   // is owed 30, owes 10 -> net +20 (is owed 20)

        System.out.println("Net balances: " + netBalances);
        System.out.println();
        System.out.println("Naive approach: 3 separate transactions (one per original expense)");
        System.out.println("Simplified settlement:");
        for (Transaction t : simplify(netBalances)) {
            System.out.printf("  %s pays %s $%.2f%n", t.from(), t.to(), t.amount());
        }
        System.out.println("(2 transactions instead of 3 - and the gap widens fast as group size grows)");
    }
}
