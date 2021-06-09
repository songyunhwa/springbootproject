package com.example.yhwasongtest.place.controller;

import com.example.yhwasongtest.place.dto.PlaceDto;
import com.example.yhwasongtest.place.model.PlaceModel;
import com.example.yhwasongtest.place.model.ReviewModel;
import com.example.yhwasongtest.place.service.PlaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(value = "/api/v1")
public class PlaceController {

    private static final Logger logger = LoggerFactory.getLogger(PlaceController.class);

    private final PlaceService placeService;

    @Autowired
    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @PostMapping(value = "/place/{name}")
    public ResponseEntity getPlace(@PathVariable String name) {
        PlaceModel placeModel = placeService.getPlace(name);
        return new ResponseEntity<>(placeModel, HttpStatus.OK);
    }

    @PostMapping(value = "/place")
    public ResponseEntity putPlace(@RequestBody PlaceDto model) {

        String result = "";
        PlaceModel placeModel = new PlaceModel();
        try {

            placeModel.setName(model.getName()); // 장소 이름
            placeModel.setArea(model.getArea()); // 지역
            placeModel.setNumber(model.getNumber());
            placeModel.setUrl(model.getUrl());
            placeModel.setSubCategory(model.getSubCategory());

            placeModel = placeService.putPlace(placeModel);

            return new ResponseEntity<>(placeModel, HttpStatus.OK);

        } catch (Exception error) {
            System.err.println("putPlace Error ==> " + error);
            return new ResponseEntity<>(placeModel, HttpStatus.BAD_REQUEST);

        }
    }

    @DeleteMapping(value = "/place/{name}")
    public ResponseEntity deletePlace(@PathVariable(name = "name",required = true) String name) {

        placeService.deletePlace(name);
        return new ResponseEntity(HttpStatus.OK);
    }

    @PostMapping(value = "/place/review")
    public ResponseEntity putReview(@RequestBody ReviewModel review) {

        try {
            ReviewModel reviewModel = placeService.putReview(review);

            return new ResponseEntity<>(reviewModel, HttpStatus.OK);
        } catch (Exception e) {
            logger.info("PlaceController.js : putReview exception cause :" , e.toString());
            return new ResponseEntity("리퀘스트 값이 없습니다.",HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping(value = "/place/review")
    public ResponseEntity deleteReview(
                                       @RequestParam(name = "userName",required = true) String userName,
                                       @RequestParam(name = "placeName",required = true) String placeName
                                       ) {
        placeService.deleteReview(userName, placeName);
        return new ResponseEntity(HttpStatus.OK);
    }

    @GetMapping(value = "/location")
    public ResponseEntity getLocation(){
        String result = placeService.getLocation();
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    public void addView(@CookieValue(name = "login", defaultValue = "null") String cookie,
                             @RequestParam(name = "id") long id,
                             @RequestParam(name = "placeName") String placeName,
                             HttpServletResponse response){
        // 쿠키에 아이디가 없다면 추가해주기.
        if(!cookie.contains("loginCookie" + placeName)){
            cookie += id + "/";
            placeService.addView(placeName);
        }
        response.addCookie(new Cookie("loginCookie" +placeName, cookie));

    }

}