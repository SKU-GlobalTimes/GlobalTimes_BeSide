package com.example.globalTimes_be.domain.trend.controller.test;

import com.example.globalTimes_be.domain.trend.dto.resonse.TrendDTO;
import com.example.globalTimes_be.domain.trend.scheduler.TrendScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/test/trend")
public class TrendTestController {
//    private final TrendScheduler trendScheduler;
//
//    @GetMapping
//    public String saveTrend(){
//        trendScheduler.saveTrendApi();
//        return "ok_!";
//    }
}
