package com.ssafy.economius.game.service;

import com.ssafy.economius.common.exception.validator.GameValidator;
import com.ssafy.economius.game.dto.EventDto;
import com.ssafy.economius.game.dto.EventMoneyDto;
import com.ssafy.economius.game.dto.EventStockDto;
import com.ssafy.economius.game.dto.response.EventCardResponse;
import com.ssafy.economius.game.entity.redis.*;
import com.ssafy.economius.game.repository.redis.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventService {

    private final GameRepository gameRepository;
    private final GameValidator gameValidator;
    private final InsuranceService insuranceService;

    public EventCardResponse visitEventCard(int roomId) {
        List<Long> bankruptcyPlayers = null;
        //게임 방 조회
        Game game = gameValidator.checkValidGameRoom(gameRepository.findById(roomId), roomId);

        //게임 속 이벤트 조회
        Event event = game.getEvent();
//        log.info(event.getEventMoney().toString());

        // 이벤트 랜덤 선정
        Random random = new Random();

        // 이벤트 카테고리 선정 (money or stock) - 실제 로직
        int randomCategory = random.nextInt(2);
        // 이벤트 카테고리 선정 (money or stock) - 시연용 로직
        //int randomCategory = random.nextInt(1);

        // 세부 이벤트 선정 및 적용
        EventDto eventDto = EventDto.builder().build();

        EventCardResponse eventCardResponse = EventCardResponse.builder().build();

        // Money 이벤트 발생
        if (randomCategory == 0) {
            EventMoney eventMoney = event.getEventMoney().get(random.nextInt(event.getEventMoney().size()));
            EventMoneyDto eventMoneyDto = EventMoneyDto.builder()
                    .eventMoneyId(eventMoney.getEventMoneyId())
                    .insuranceTypeId(eventMoney.getInsuranceTypeId())
                    .typeCode(eventMoney.getTypeCode())
                    .typeName(eventMoney.getName())
                    .name(eventMoney.getName())
                    .description(eventMoney.getDescription())
                    .money(eventMoney.getMoney())
                    .url(eventMoney.getUrl())
                    .build();
            eventDto.setEventMoneyDto(eventMoneyDto);

            // 모든 플레이어의 보험 확인 & 적용
            for (Long player : game.getPlayers()) {
                int payment = insuranceService.applyInsurance(
                        game,
                        player,
                        eventMoney.getInsuranceTypeId(),
                        eventMoney.getMoney());

                // 보유 현금이 부족하면 파산
                int resultMoney = game.getPortfolios().get(player).getMoney() - payment;

                if (resultMoney < 0) {
                    if (bankruptcyPlayers == null) bankruptcyPlayers = new ArrayList<>();
                    game.proceedBankruptcy(player);
                    bankruptcyPlayers.add(player);
                    gameValidator.throwBankruptcyResponse(roomId, player);
                }

                eventCardResponse = EventCardResponse.builder()
                        .isMoneyCard(true)
                        .name(eventDto.getEventMoneyDto().getName())
                        .description(eventDto.getEventMoneyDto().getDescription())
                        .eventValue(eventDto.getEventMoneyDto().getMoney())
                        .url(eventDto.getEventMoneyDto().getUrl())
                        .apply(eventDto.getEventMoneyDto().getTypeCode())
                        .build();
                game.getPortfolios().get(player).setMoney(resultMoney);
            }
        }
        // Stock 이벤트 발생
        else {
            EventStock eventStock = event.getEventStock().get(random.nextInt(event.getEventStock().size()));
            EventStockDto eventStockDto = EventStockDto.builder()
                    .eventStockId(eventStock.getEventStockId())
                    .stockIndustryId(eventStock.getStockIndustryId())
                    .industry(eventStock.getIndustry())
                    .name(eventStock.getName())
                    .description(eventStock.getDescription())
                    .rate(eventStock.getRate())
                    .url(eventStock.getUrl())
                    .build();
            eventDto.setEventStockDto(eventStockDto);

            for (Stock stock : game.getStocks().values()) {
                if (stock.getStockIndustryId() == eventStockDto.getStockIndustryId()) {
                    log.info(stock.toString());

                    stock.updateStockPriceAndRate(eventStockDto.getRate(), game.getGameTurn() / 4);
                    game.getPortfolios().values()
                            .forEach(portfolio -> portfolio.getStocks().updatePortfolioStockByStockChange(stock));

                }
            }
            eventCardResponse = EventCardResponse.builder()
                    .isMoneyCard(false)
                    .name(eventDto.getEventStockDto().getName())
                    .description(eventDto.getEventStockDto().getDescription())
                    .eventValue(eventDto.getEventStockDto().getRate())
                    .url(eventDto.getEventStockDto().getUrl())
                    .apply(eventDto.getEventStockDto().getIndustry())
                    .build();
        }

        gameRepository.save(game);

        return eventCardResponse;
    }
}
