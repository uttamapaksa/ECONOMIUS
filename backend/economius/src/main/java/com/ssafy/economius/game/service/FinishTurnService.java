package com.ssafy.economius.game.service;

import com.ssafy.economius.common.exception.validator.GameValidator;
import com.ssafy.economius.game.dto.mysql.IssueStockDto;
import com.ssafy.economius.game.entity.redis.Building;
import com.ssafy.economius.game.entity.redis.Game;
import com.ssafy.economius.game.entity.redis.Stock;
import com.ssafy.economius.game.enums.ChangeUnit;
import com.ssafy.economius.game.enums.InitialData;
import com.ssafy.economius.game.enums.VolatileEnum;
import com.ssafy.economius.game.repository.redis.GameRepository;
import com.ssafy.economius.game.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.ssafy.economius.game.enums.RateEnum.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class FinishTurnService {

    private final GameRepository gameRepository;
    private final GameValidator gameValidator;

    public Game finish(int roomId) {
        Game game = gameValidator.checkValidGameRoom(gameRepository.findById(roomId), roomId);

        int gameTurn = game.updateGameTurn();
        int round = gameTurn / 4;

        // todo 각종 이벤트 로직 구현
        // 주식가격 재 산정
        stockRearrange(game, round);
        // 새로운 라운드일경우
        if (gameTurn % 4 == 0) {
            goldRearrange(game);
            buildingRearrange(game);
            interestRateRearrange(game);

            changePrevIssue(game, round);
            checkIssueRound(game, round);
            applyIssueEffect(game, round);
        }

        gameRepository.save(game);
        return game;
    }

    private void changePrevIssue(Game game, int round) {
        if (round % 4 != 3) return;
        int idx = game.getIssueIdx() + 1;
        game.setIssueIdx(idx);
        game.setCurrentPrevIssue(
                InitialData.getPrevIssue(game.getIssues().get(idx).getIssueId()));
        game.setCurrentIssue(null);
    }

    private void checkIssueRound(Game game, int round) {
        if (round % 4 != 0) return;
        game.setCurrentIssue(game.getIssues().get(game.getIssueIdx()));
    }

    private void applyIssueEffect(Game game, int round) {
        if (round % 4 == 3) return;
        int issueId = game.getIssues().get(game.getIssueIdx()).getIssueId();
        List<IssueStockDto> assetsChanges = InitialData.getIssueChanges(issueId);
        log.info(assetsChanges.toString());

        for (IssueStockDto assetChange : assetsChanges) {
            applyChanges(game, assetChange, round);
        }
    }

    private void applyChanges(Game game, IssueStockDto assetChange, int round) {
        String type = assetChange.getAssetType();
        int changeRate = getChangeRate(assetChange.getChangeUnit());

        if (type.equals(VolatileEnum.GOLD.getValue())) {
            game.getGold().updateGoldPrice(changeRate);
        } else if (type.equals(VolatileEnum.INTEREST_RATE.getValue())) {
            game.getInterestRate().updateBuildingPrice(changeRate);
        } else if (type.equals(VolatileEnum.BUIDING.getValue())) {
            applyBuildingChanges(game, changeRate);
        } else if (type.equals(VolatileEnum.STOCK.getValue())) {
            applyStockChanges(game, assetChange.getAssetId(), changeRate, round);
        }
    }

    private int getChangeRate(int changeUnit) {
        for (ChangeUnit c : ChangeUnit.values()) {
            if (changeUnit == c.getCode()) return c.getValue();
        }
        return 1;
    }

    private void applyBuildingChanges(Game game, int changeRate) {
        for (int buildingId : game.getBuildings().keySet()) {
            updateBuildingPrice(changeRate, game, buildingId);
        }
    }

    private void applyStockChanges(Game game, int assetId, int changeRate, int round) {
        Stock stock = game.getStocks().get(assetId);
        updateStock(game, stock, changeRate, round);
    }

    private void interestRateRearrange(Game game) {
        int newPrice = RandomUtil.getRanges(INTEREST_RATE_LOWER_BOUND.getValue(),
                INTEREST_RATE_UPPER_BOUND.getValue());
        game.getInterestRate().updateBuildingPrice(newPrice);
    }

    private void buildingRearrange(Game game) {
        for (int buildingId : game.getBuildings().keySet()) {
            // 가격 재조정
            int newRate = RandomUtil.getRanges(BUILDING_RATE_LOWER_BOUND.getValue(),
                    BUILDING_RATE_UPPER_BOUND.getValue());
            updateBuildingPrice(newRate, game, buildingId);
        }
    }

    private void updateBuildingPrice(int newRate, Game game, int buildingId) {
        Building building = game.getBuildings().get(buildingId);
        building.updateBuildingPrice(newRate);
        // 포폴 부동산 업데이트
        game.getPortfolios().get(building.getOwnerId())
                .getBuildings().updateBuildingInfo(buildingId, building);
    }

    private void goldRearrange(Game game) {
        int newRate = RandomUtil.getRanges(GOLD_RATE_LOWER_BOUND.getValue(),
                GOLD_RATE_UPPER_BOUND.getValue());
        game.getGold().updateGoldPrice(newRate);
    }

    private void stockRearrange(Game game, int round) {
        game.getStocks().values().forEach(stock -> {
            int newRate = RandomUtil.getRanges
                    (STOCK_RATE_LOWER_BOUND.getValue(), STOCK_RATE_UPPER_BOUND.getValue());
            updateStock(game, stock, newRate, round);
        });
    }

    private void updateStock(Game game, Stock stock, int newRate, int round) {
        stock.updateStockPriceAndRate(newRate, round);
        log.info("게임 턴 변경 ->" + stock);
        game.getPortfolios().values()
                .forEach(portfolio -> portfolio.getStocks().updatePortfolioStockByStockChange(stock));
    }



}
