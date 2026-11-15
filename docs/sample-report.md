# Debugging report

2 distinct error(s) found in the logs.

## 1. NullPointerException in `DiscountService.applyCoupon`

- **Occurrences:** 3 (first 2026-01-12T09:14:22Z, last 2026-01-12T11:05:31Z)
- **Service:** orders-service
- **Logged as:** Checkout failed for order 10482
- **Exception:** `java.lang.NullPointerException: Cannot invoke "demo.orders.DiscountService$Coupon.percentOff()" because "coupon" is null`
- **Location:** `DiscountService.java`, line 17

```java
    13      }
    14  
    15      public BigDecimal applyCoupon(BigDecimal total, String couponCode) {
    16          Coupon coupon = coupons.get(couponCode);
>>  17          BigDecimal percent = coupon.percentOff();
    18          return total.subtract(total.multiply(percent).movePointLeft(2));
    19      }
    20  
    21      public record Coupon(String code, BigDecimal percentOff) {}
```

**Root cause:** `coupon` is null when this line runs: `BigDecimal percent = coupon.percentOff();`. The code assumes the value is always present.

**Suggested fix:** Handle the missing value before using it: return early, apply a default, or reject the input with a clear error.

**Regression test draft:** `regression-tests/DiscountServiceApplyCouponRegressionTest.java`

**Verification:** RESOLVED. The error does not appear in the post-fix logs.

_Analyst: rule-based (offline)_

## 2. ArithmeticException in `OrderService.averageItemPriceCents`

- **Occurrences:** 2 (first 2026-01-12T09:31:47Z, last 2026-01-12T11:48:09Z)
- **Service:** orders-service
- **Logged as:** Order summary failed for cart 7731
- **Exception:** `java.lang.ArithmeticException: / by zero`
- **Location:** `OrderService.java`, line 25

```java
    21          int sum = 0;
    22          for (int price : itemPricesCents) {
    23              sum += price;
    24          }
>>  25          return sum / itemPricesCents.size();
    26      }
    27  }
```

**Root cause:** Integer division by zero at: `return sum / itemPricesCents.size();`. The divisor is a collection size, so the input was empty.

**Suggested fix:** Guard the divisor: return a defined result for the empty case before dividing.

**Regression test draft:** `regression-tests/OrderServiceAverageItemPriceCentsRegressionTest.java`

**Verification:** STILL OCCURRING. Seen 1 time(s) in the post-fix logs.

_Analyst: rule-based (offline)_
