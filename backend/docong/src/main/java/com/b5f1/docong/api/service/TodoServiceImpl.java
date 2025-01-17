package com.b5f1.docong.api.service;

import com.b5f1.docong.api.dto.request.ModifyTodoActivateReqDto;
import com.b5f1.docong.api.dto.request.ModifyTodoStatusReqDto;
import com.b5f1.docong.api.dto.request.PredictPomoReqDto;
import com.b5f1.docong.api.dto.request.SaveTodoReqDto;
import com.b5f1.docong.api.dto.response.FindTodoResDto;
import com.b5f1.docong.api.dto.response.PredictPomoResDto;
import com.b5f1.docong.api.exception.CustomException;
import com.b5f1.docong.api.exception.ErrorCode;
import com.b5f1.docong.core.domain.group.Team;
import com.b5f1.docong.core.domain.todo.Todo;
import com.b5f1.docong.core.domain.todo.UserTodo;
import com.b5f1.docong.core.domain.user.User;
import com.b5f1.docong.core.queryrepository.TodoQueryRepository;
import com.b5f1.docong.core.repository.TeamRepository;
import com.b5f1.docong.core.repository.TodoRepository;
import com.b5f1.docong.core.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TodoServiceImpl implements TodoService{
    private final TodoRepository todoRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TodoQueryRepository todoQueryRepository;
    @Override
    public Long saveTodo(SaveTodoReqDto reqDto) {
        User user = userRepository.findByEmailAndActivateTrue(reqDto.getUserEmail());

        Todo todo = reqDto.toEntity();
        UserTodo userTodo = UserTodo.builder()
                .build();

        todo.addUserTodo(userTodo);
        user.addUserTodo(userTodo);

        if(reqDto.getTeamId() != null){
            Team team = teamRepository.findById(reqDto.getTeamId())
                    .orElseThrow(()->new CustomException(ErrorCode.TEAM_NOT_FOUND));
            todo.addTeam(team);
        }

        return todoRepository.save(todo).getSeq();
    }

    @Override
    public void deleteTodo(Long id) {
        Todo todo = getTodo(id);
        todo.deleteTodo();
    }

    @Override
    public void modifyTodo(Long id, SaveTodoReqDto reqDto) {
        Todo todo = getTodo(id);
        todo.changeTodo(reqDto.getTitle(), reqDto.getContent(), reqDto.getWorkImportance(),reqDto.getWorkProficiency(),reqDto.getWorkType(), reqDto.getPredictedPomo());
        if(todo.getUserTodo().getUser().getEmail() != reqDto.getUserEmail()){
            User changedUser = userRepository.findByEmailAndActivateTrue(reqDto.userEmail);
            todo.changeUser(changedUser);
        }
    }

    @Override
    public void modifyStatus(Long id, ModifyTodoStatusReqDto reqDto) {
        Todo todo = getTodo(id);
        todo.changeStatus(reqDto.getTodoStatus());
    }

    @Override
    public void modifyActivate(Long id, ModifyTodoActivateReqDto reqDto) {
        Todo todo = getTodo(id);
        todo.changeActivation(reqDto.getActivate());

        // Slack 메세지 보내기
        if(todo.getTeam() != null && todo.getTeam().getSlackToken() != null)
            this.sendSlackMessage(todo, reqDto.getActivate());
    }

    @Override
    public FindTodoResDto findTodo(Long id) {
        Todo todo = getTodo(id);
        return new FindTodoResDto(todo);
    }

    @Override
    public List<FindTodoResDto> findUserTodos(Long userSeq) {
        return todoQueryRepository.findAllWithUserId(userSeq);
    }

    @Override
    public List<FindTodoResDto> findGroupTodos(Long groupSeq) {
        return todoQueryRepository.findAllWithGroupId(groupSeq);
    }

    @Override
    public PredictPomoResDto predictPomo(PredictPomoReqDto reqDto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        JSONObject entity = new JSONObject();
        entity.put("birth", reqDto.getBirth());
        entity.put("gender", reqDto.getGender());
        entity.put("job", reqDto.getJob());
        entity.put("position", reqDto.getPosition());
        entity.put("mbti", reqDto.getMbti());
        entity.put("importance", reqDto.getImportance().ordinal());
        entity.put("proficiency", reqDto.getProficiency().ordinal());
        entity.put("type", reqDto.getType().ordinal());
        entity.put("start_time", reqDto.getStart_time());
        entity.put("end_time", reqDto.getEnd_time());
        entity.put("time_status", reqDto.getTime_status());

        HttpEntity<String> request = new HttpEntity<>(entity.toString(), headers);

        RestTemplate rt = new RestTemplate();

        ResponseEntity<PredictPomoResDto> response = rt.exchange(
                "http://j6s003.p.ssafy.io:8185/api/predict",
                HttpMethod.POST,
                request,
                PredictPomoResDto.class
        );

        return response.getBody();
    }

    private Todo getTodo(Long id){
        return todoRepository.findById(id)
                .orElseThrow(()->new CustomException(ErrorCode.TODO_NOT_FOUND));
    }

    private void sendSlackMessage(Todo todo, Boolean activate) {
        String message = (activate)? "> :writing_hand: *["+todo.getUserTodo().getUser().getName()+"]* 님이 *["+todo.getTitle()+"]* 업무 집중을 시작했어요!"
                                    : "> :raised_hands: *["+todo.getUserTodo().getUser().getName()+"]* 님이 쉬고 있어요!";
        String token = todo.getTeam().getSlackToken();
        String channel = todo.getTeam().getSlackChannel();

        try{
            MethodsClient methods = Slack.getInstance().methods(token);
            ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                    .channel(channel)
                    .text(message)
                    .build();

            methods.chatPostMessage(request);

        }catch (Exception e){
            throw new CustomException(ErrorCode.FAIL_SEND_SLACK);
        }
    }

}
