package io.github.lino9999.chatgames.prize;

import java.util.List;

public record PrizePool(String poolId, double weight, int minOnline, String requiredPermission, List<PrizeAction> actions) {
}
