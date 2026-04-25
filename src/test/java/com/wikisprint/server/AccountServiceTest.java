package com.wikisprint.server;

import com.wikisprint.server.global.common.storage.FileStorageService;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.service.AccountDeletionExecutor;
import com.wikisprint.server.service.AccountService;
import com.wikisprint.server.service.NicknameGenerator;
import com.wikisprint.server.vo.AccountVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private AccountDeletionExecutor accountDeletionExecutor;

    @Mock
    private NicknameGenerator nicknameGenerator;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                accountMapper,
                fileStorageService,
                accountDeletionExecutor,
                nicknameGenerator
        );
    }

    @Test
    void deleteExpiredAccounts_continuesWhenOneAccountFails() {
        AccountVO first = new AccountVO();
        first.setUuid("ACC-1");
        first.setDeletionRequestedAt(LocalDateTime.now().minusDays(8));

        AccountVO second = new AccountVO();
        second.setUuid("ACC-2");
        second.setDeletionRequestedAt(LocalDateTime.now().minusDays(9));

        when(accountMapper.selectExpiredDeletionAccounts(100)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("failed")).when(accountDeletionExecutor).deleteAccountInNewTransaction("ACC-1");

        int deletedCount = accountService.deleteExpiredAccounts();

        assertThat(deletedCount).isEqualTo(1);
        verify(accountDeletionExecutor).deleteAccountInNewTransaction("ACC-1");
        verify(accountDeletionExecutor).deleteAccountInNewTransaction("ACC-2");
    }

    @Test
    void deleteAccountImmediately_delegatesToExecutor() {
        accountService.deleteAccountImmediately("ACC-1");

        verify(accountDeletionExecutor).deleteAccount("ACC-1");
    }
}
