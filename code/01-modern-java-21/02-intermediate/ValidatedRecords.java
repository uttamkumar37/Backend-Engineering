import java.math.BigDecimal;
import java.util.List;

public class ValidatedRecords {

    record Money(BigDecimal amount, String currency) {
        Money {
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("amount cannot be negative: " + amount);
            }
            if (currency == null || currency.length() != 3) {
                throw new IllegalArgumentException("currency must be a 3-letter ISO code: " + currency);
            }
        }
    }

    // without List.copyOf, callers could mutate the "immutable" record's backing list
    record Cart(List<String> itemIds) {
        Cart {
            itemIds = List.copyOf(itemIds);
        }
    }

    public static void main(String[] args) {
        Money price = new Money(new BigDecimal("199.99"), "USD");
        System.out.println(price);

        try {
            new Money(new BigDecimal("-5"), "USD");
        } catch (IllegalArgumentException e) {
            System.out.println("Rejected negative amount: " + e.getMessage());
        }

        List<String> mutableList = new java.util.ArrayList<>(List.of("item-1", "item-2"));
        Cart cart = new Cart(mutableList);
        mutableList.add("item-3-added-after-construction");

        System.out.println("Cart still has: " + cart.itemIds());

        try {
            cart.itemIds().add("should-fail");
        } catch (UnsupportedOperationException e) {
            System.out.println("Cart's internal list is truly unmodifiable, as expected.");
        }
    }
}
