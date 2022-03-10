package com.b5f1.docong.api.service;

import com.b5f1.docong.api.dto.request.SaveTeamReqDto;
import com.b5f1.docong.api.dto.request.UpdateTeamReqDto;
import com.b5f1.docong.api.dto.response.FindTeamResDto;
import com.b5f1.docong.core.domain.group.Team;

public interface TeamService {
    Long updateTeam(UpdateTeamReqDto teamReqDto);
    Long createTeam(SaveTeamReqDto teamReqDto);
    void deleteTeam(Long team_id);
    FindTeamResDto findTeam(Long team_id);
}
