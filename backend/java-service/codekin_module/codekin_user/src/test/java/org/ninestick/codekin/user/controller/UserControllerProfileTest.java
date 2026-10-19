package org.ninestick.codekin.user.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.ninestick.codekin.user.service.IUserAvatarService;
import org.ninestick.codekin.user.domain.dto.info.DefaultAvatarUpdateDto;
import org.ninestick.codekin.user.domain.vo.info.DefaultAvatarVo;
import org.springframework.http.MediaType;
import org.junit.jupiter.params.provider.ValueSource;
import org.ninestick.codekin.user.domain.vo.info.MyUserVo;
import org.ninestick.codekin.user.service.IUserService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 本人资料 HTTP 响应的密码状态契约；使用模拟业务隔离基础设施，仅验证 MVC 序列化。
 *
 * @author ninestick
 */
class UserControllerProfileTest {

    /** 无密码与有密码均返回显式布尔值，不因 false 被省略；响应不泄漏密码散列。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void myProfileAlwaysReturnsBooleanPasswordStatus(boolean hasPassword) throws Exception {
        IUserService service = mock(IUserService.class);
        when(service.getMe()).thenReturn(MyUserVo.builder().userId("42")
                .nickname("测试用户").hasPassword(hasPassword).build());
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", service);

        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.hasPassword").isBoolean())
                .andExpect(jsonPath("$.data.hasPassword").value(hasPassword))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void defaultListReturnsStableKeysAndTemporaryDisplayUrls() throws Exception {
        var avatars = mock(IUserAvatarService.class);
        when(avatars.getDefaultAvatars()).thenReturn(List.of(DefaultAvatarVo.builder().name("眨眼")
                .objectKey("uploads/avatar/default/avatar-01-wink.png").url("https://storage.example/signed").build()));
        var controller = new UserController();
        ReflectionTestUtils.setField(controller, "userAvatarService", avatars);
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/users/avatar/defaults"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].objectKey").value("uploads/avatar/default/avatar-01-wink.png"))
                .andExpect(jsonPath("$.data[0].url").value("https://storage.example/signed"));
    }

    @Test
    void selectionBindsObjectKeyAndRejectsExternalUrlsBeforeBusiness() throws Exception {
        var avatars = mock(IUserAvatarService.class);
        var controller = new UserController();
        ReflectionTestUtils.setField(controller, "userAvatarService", avatars);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(put("/api/users/me/avatar/default").contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectKey\":\"https://example.com/avatar.png\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(avatars);
        mvc.perform(put("/api/users/me/avatar/default").contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectKey\":\"uploads/avatar/default/avatar-01-wink.png\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
        var request = ArgumentCaptor.forClass(DefaultAvatarUpdateDto.class);
        verify(avatars).updateDefaultAvatar(request.capture());
        assertEquals("uploads/avatar/default/avatar-01-wink.png", request.getValue().getObjectKey());
    }
}
