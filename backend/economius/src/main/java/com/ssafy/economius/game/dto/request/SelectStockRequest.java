package com.ssafy.economius.game.dto.request;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class SelectStockRequest {

    private Long player;
    private int stockId;

}
