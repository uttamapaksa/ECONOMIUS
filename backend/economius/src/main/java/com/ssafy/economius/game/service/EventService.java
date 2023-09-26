package com.ssafy.economius.game.service;

import com.ssafy.economius.common.exception.validator.GameValidator;
import com.ssafy.economius.game.dto.EventDto;
import com.ssafy.economius.game.dto.EventMoneyDto;
import com.ssafy.economius.game.dto.EventStockDto;
import com.ssafy.economius.game.dto.SavingDto;
import com.ssafy.economius.game.dto.request.EventCardRequest;
import com.ssafy.economius.game.dto.request.SavingRequest;
import com.ssafy.economius.game.dto.response.EventCardResponse;
import com.ssafy.economius.game.dto.response.SavingVisitResponse;
import com.ssafy.economius.game.entity.redis.*;
import com.ssafy.economius.game.repository.redis.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventService {

    private final GameRepository gameRepository;
    private final GameValidator gameValidator;

    public EventCardResponse visitEventCard(int roomId, EventCardRequest eventCardRequest) {
        //게임 방 조회
        Game game = gameValidator.checkValidGameRoom(gameRepository.findById(roomId), roomId);

        //게임 속 이벤트 조회
        Event event = game.getEvent();

        log.info(event.getEventMoney().toString());
        log.info(event.getEventStock().toString());

        // 이벤트 랜덤 선정
        Random random = new Random();

        // 이벤트 카테고리 선정 (money stock)
        int randomCategory = random.nextInt(2);

        // 세부 이벤트 선정 및 적용
        EventDto eventDto = EventDto.builder().build();

        if(randomCategory == 0) {
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
        }
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
        }


        // 이벤트 적용


        // 어떤 이벤트인지 response 던져주기
        EventCardResponse eventCardResponse = EventCardResponse.builder()
                .eventDto(eventDto)
                .build();

        return eventCardResponse;
    }
}