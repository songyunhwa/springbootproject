package com.example.yhwasongtest.user.service;

import com.example.yhwasongtest.common.ErrorMessage;
import com.example.yhwasongtest.user.dto.UserModelDto;
import com.example.yhwasongtest.user.model.LoginHistory;
import com.example.yhwasongtest.user.model.UserModel;
import com.example.yhwasongtest.user.repository.AuthorityRepository;
import com.example.yhwasongtest.user.repository.LoginHistoryRepository;
import com.example.yhwasongtest.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
public class UserService implements UserDetailsService{
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    private final JavaMailSender javaMailSender;

    @Value(value = "${frontend.api.url}")
    String front_api;


    public UserService(UserRepository userRepository,
                       AuthorityRepository authorityRepository,
                       LoginHistoryRepository loginHistoryRepository,
                       JavaMailSender javaMailSender) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.javaMailSender = javaMailSender;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserModel userModel = userRepository.findByUsername(username);
        List<GrantedAuthority> authorityList = new ArrayList<>();
        authorityList.add(new SimpleGrantedAuthority(userModel.getRole()));
        return new User(userModel.getUsername(), userModel.getPassword(), authorityList);
    }

    public UserModel signUp(UserModelDto userModelDto) throws Exception {
        UserModel userModel = userRepository.findByEmail(userModelDto.getEmail());

        if (userModel != null) {
            throw new Exception(ErrorMessage.EMAIL_DUPLICATION.getMessage());
        }

        userModel = insertUser(userModelDto);
        return userModel;
    }

    public void changePassword(UserModelDto userModelDto) throws Exception{
        // 가입된 것이 있는지 확인
        UserModel userModel = userRepository.findByEmail(userModelDto.getEmail());
        if  (userModel == null) {
            throw new Exception(ErrorMessage.SIGNUP_EMAIL_INVALID.getMessage());
        }

        String resultToken = getToken(userModel.getUsername(), userModelDto.getPassword());
        resultToken = getHashed(resultToken);
        if(userModel.getPassword().equals(resultToken)) {
            throw new Exception(ErrorMessage.PASSWORD_EQUIL.getMessage());
        }
        this.insertUser(userModelDto);
    }

    public UserModel insertUser(UserModelDto userModelDto) throws Exception {
        String resultToken = getToken(userModelDto.getUsername(), userModelDto.getPassword());
        resultToken = getHashed(resultToken);

        // 회원가입과 비밀번호 변경에 쓰임
        UserModel userModel = userRepository.findByEmail(userModelDto.getEmail());
        if(userModel == null) {
            userModel = new UserModel();
            userModel.setUsername(userModelDto.getUsername());
            userModel.setEmail(userModelDto.getEmail());
            userModel.setRole("ROLE_USER");
            userModel.setPassword(resultToken);
        }else {
            userModel.setPassword(resultToken);
        }
        userRepository.save(userModel);
        return userModel;
    }

    public UserModel getUser(String userName) {
        return userRepository.findByUsername(userName);
    }

    public String getToken(String id, String password) throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> object = new HashMap<String, String>();
        object.put("typ", "JWT");
        object.put("alg", "HS256");
        String bytes = mapper.writeValueAsString(object);
        String headerResult = Base64.getUrlEncoder().encodeToString(bytes.getBytes());
        headerResult = headerResult.replaceAll("=", "");

        Map<String, String> object1 = new HashMap<String, String>();
        object1.put("iss", "mapyhwasong.com");
        object1.put("exp", "1485270000000");
        object1.put("https://github.com/songyunhwa/springBootProject_back", "true");
        object1.put("userId", id);
        object1.put("password", password);
        String bytes1 = mapper.writeValueAsString(object1);
        String bodyResult = Base64.getUrlEncoder().encodeToString(bytes1.getBytes());
        bodyResult = bodyResult.replaceAll("=", "");

        return headerResult + "." + bodyResult;
    }

    public String getHashed(String password) {
        String passwordHashed = BCrypt.hashpw(password, BCrypt.gensalt());
        return passwordHashed;
    }

    public UserModel login(String name, String password, HttpServletRequest request) throws Exception {

        UserModel userModel = userRepository.findByUsername(name);

        // 구글 로그인을 사용한 경우
        if (userModel == null) {
            userModel = userRepository.findByEmail(name);
        }

        /* 토큰을 사용한 경우
        if (token != null) {
            if (tokenProvider.validateToken(token)) {
                String subject = tokenProvider.getSubject(token);
                JSONParser parser = new JSONParser();
                Object obj = parser.parse( subject );
                JSONObject jsonObj = (JSONObject) obj;

                Long id = (Long) jsonObj.get("id");
                userModel = userRepository.findById(id).get();
            }
        }*/

        if (userModel == null) {
            throw new Exception(ErrorMessage.SIGNUP_EMAIL_INVALID.getMessage());
        }
        if (userModel.getPassword().length() == 0 || userModel.getPassword() == null) {
            throw new Exception(ErrorMessage.SIGNUP_GOOGLE_PREV_INVALID.getMessage());
        }

        String ip = this.getRemoteAddr(request);

        if (userModel != null) {
            String resultToken = getToken(name, password);
            //if (userModel.getPassword().equals(resultToken)) {
            if (BCrypt.checkpw(resultToken, userModel.getPassword())) {

                // 세션 설정
                HttpSession session = request.getSession();
                session.setAttribute("login", userModel);
                session.setMaxInactiveInterval(1800); //30분

                // 세션 유효시간 설정
                Date date = new Date(System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 7));
                this.keepLogin(userModel.getUsername(), session.getId(), date);

                this.putHistory(userModel.getUsername(), ip);

                // 토큰 생성
                //  token= tokenProvider.createToken(userModel.toString());

            } else throw new Exception(ErrorMessage.SIGNUP_PWD_INVALID.getMessage());
        } else throw new Exception(ErrorMessage.SIGNUP_EMAIL_INVALID.getMessage());

        return userModel;
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public void keepLogin(String username, String sessionId, Date date) {

        UserModel userModel = userRepository.findByUsername(username);
        if (userModel != null) {
            userModel.setSessionId(sessionId);
            userModel.setDate(date);
            userRepository.save(userModel);
        }
    }

    public void putHistory(String userName, String ip) throws ParseException {

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date currentDate = new Date();
        String date = format.format(currentDate);

        String start = date + " 00:00:00";
        String end = date + " 23:59:59";

        format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // 유저가 오늘 접속했는지 // 같은 ip를 갖고 있는지 확인
        LoginHistory loginHistory = loginHistoryRepository.findByStatusAndUserNameAndLoginDateBetween("LOGIN", userName, format.parse(start), format.parse(end));
        if (loginHistory != null) {
            loginHistory.setLoginDate(new Date());
        } else {
            loginHistory = new LoginHistory();
            loginHistory.setIp(ip);
            loginHistory.setUserName(userName);
            loginHistory.setStatus("LOGIN");
            loginHistory.setLoginDate(new Date());
        }

        loginHistoryRepository.save(loginHistory);

    }

    public String getRemoteAddr(HttpServletRequest request) {

        String ip = null;

        ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;

    }

    public int getLoginHistory() throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date currentDate = new Date();
        String date = format.format(currentDate);

        String start = date + " 00:00:00";
        String end = date + " 23:59:59";

        format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<LoginHistory> loginHistories = loginHistoryRepository.findByStatusAndLoginDateBetween("LOGIN", format.parse(start), format.parse(end));
        return loginHistories.size();
    }

    public void sendGoogleMail(String username, String email) throws Exception{
        // 정보가 유효한지 확인
        UserModel userModel = userRepository.findByEmail(email);
        if (userModel == null) {
            throw new Exception(ErrorMessage.SIGNUP_EMAIL_INVALID.getMessage());
        }
        if (!userModel.getUsername().equals(username)) {
            throw new Exception(ErrorMessage.SIGNUP_MATCH_INVALID.getMessage());
        }

        /* 메일 전송 */
        MimeMessagePreparator messagePreparator = new MimeMessagePreparator() {
            @Override
            public void prepare(MimeMessage mimeMessage) throws Exception {
                MimeMessageHelper message = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                message.setTo(email);
                message.setFrom("happy0155@naver.com");
                message.setSubject("[맛따라멋따라] 비밀번호 변경 안내");
                message.setText("비밀번호를 변경하시려면 아래의 url 를 클릭하세요. \n" +
                        front_api + "/password/" + email);
            }
        };

        try {
            javaMailSender.send(messagePreparator);
        } catch (Exception e) {
            logger.info("sendGoogleMail error => " + e.getMessage());
        }

    }


}