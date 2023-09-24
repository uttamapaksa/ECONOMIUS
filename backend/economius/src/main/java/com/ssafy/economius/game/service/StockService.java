package com.ssafy.economius.game.service;

import com.ssafy.economius.common.exception.CustomWebsocketException;
import com.ssafy.economius.common.exception.message.GameRoomMessage;
import com.ssafy.economius.common.exception.validator.GameValidator;
import com.ssafy.economius.game.dto.response.BuyItemResponse;
import com.ssafy.economius.game.dto.response.BuyStockResponse;
import com.ssafy.economius.game.dto.response.SellStockResponse;
import com.ssafy.economius.game.entity.redis.Game;
import com.ssafy.economius.game.entity.redis.Portfolio;
import com.ssafy.economius.game.entity.redis.Stock;
import com.ssafy.economius.game.repository.redis.GameRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final GameRepository gameRepository;
    private final GameValidator gameValidator;

    public BuyItemResponse buyItem(int roomId, int stockId, Long buyPlayer) {
        /**
         * stockId 에 해당하는 주식을 찾음
         * stockId 에 해당하는 오너들을 가지고옴
         * 주식 하나의 가격에 해당하는 가격만큼 플레이어에게 징수
         * 징수한 돈을 100으로 나눈값 * 주식 보유량을 기준으로 플레이어들에게 전송
         */
        Game game = gameValidator.checkValidGameRoom(gameRepository.findById(roomId), roomId);

        Portfolio portfolio = game.getPortfolios().get(buyPlayer);

        Stock stock = game.getStocks().get(stockId);

        if (portfolio.getMoney() - stock.getPrice() < 0) {
            gameValidator.throwBankruptcyResponse(roomId, buyPlayer);
        }

        portfolio.setMoney(portfolio.getMoney() - stock.getPrice());

        Map<Long, Integer> owners = stock.getOwners();
        for (Long player : owners.keySet()) {
            if (owners.get(player) != 0) {
                Portfolio portfolio1 = game.getPortfolios().get(player);
                portfolio1.setMoney(portfolio1.getMoney()
                    + (int) ((double) owners.get(player) / 100 * stock.getPrice()));
                log.info(player + " : " + portfolio1.getMoney());
                portfolio1.updateTotalMoney();
            }
        }

        gameRepository.save(game);
        return new BuyItemResponse(buyPlayer);
    }

    public BuyStockResponse buyStock(int roomId, int stockId, int stockAmount, Long player) {
        Game game = gameValidator.checkValidGameRoom(gameRepository.findById(roomId), roomId);

        // 갯수는 문제없는지
        Stock stock = game.getStocks().get(stockId);

        // 금액은 문제 없는지
        Portfolio portfolio = game.getPortfolios().get(player);
        validateStockToBuy(roomId, stockAmount, stock, portfolio);

        // 모두 통과했다..
        stock.dealStock(player, stockAmount);
        portfolio.getStocks().updatePortfolioStock(stock, stockAmount);

        gameRepository.save(game);

        return new BuyStockResponse(player);
    }


    public SellStockResponse sellStock(int roomId, int stockId, int stockAmount, Long player) {
        Game game = gameValidator.checkValidGameRoom(gameRepository.findById(roomId), roomId);
        stockAmount *= -1;
        Stock stock = game.getStocks().get(stockId);
        Portfolio portfolio = game.getPortfolios().get(player);

        validateStockToBuy(roomId, stockAmount, stock, portfolio);

        stock.dealStock(player, stockAmount);
        portfolio.getStocks().updatePortfolioStock(stock, stockAmount);

        gameRepository.save(game);

        return new SellStockResponse(player);
    }

    private void validateStockToBuy(int roomId, int stockAmount, Stock stock, Portfolio portfolio) {
        int money = portfolio.getMoney();
        int price = stock.getPrice() * stockAmount;
        gameValidator.canBuy(roomId, money, price);

        if (stock.checkStockAvailableToPurchase(stockAmount)) {
            throw CustomWebsocketException.builder()
                .roomId(roomId)
                .code(GameRoomMessage.NOT_ENOUGH_STOCK.getCode())
                .message(GameRoomMessage.NOT_ENOUGH_STOCK.getMessage())
                .build();
        }
    }

}
