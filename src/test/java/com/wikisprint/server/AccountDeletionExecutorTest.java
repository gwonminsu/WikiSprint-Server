package com.wikisprint.server;

import com.wikisprint.server.global.common.storage.FileStorageService;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.mapper.ConsentMapper;
import com.wikisprint.server.mapper.DonationMapper;
import com.wikisprint.server.mapper.GameRecordMapper;
import com.wikisprint.server.mapper.RankingMapper;
import com.wikisprint.server.mapper.ReportMapper;
import com.wikisprint.server.mapper.SharedGameRecordMapper;
import com.wikisprint.server.service.AccountDeletionExecutor;
import com.wikisprint.server.vo.AccountVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionExecutorTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private GameRecordMapper gameRecordMapper;

    @Mock
    private RankingMapper rankingMapper;

    @Mock
    private ConsentMapper consentMapper;

    @Mock
    private SharedGameRecordMapper sharedGameRecordMapper;

    @Mock
    private DonationMapper donationMapper;

    @Mock
    private ReportMapper reportMapper;

    @Test
    void deleteAccount_unlinksRetainedRecordsBeforeAccountDeletion() {
        AccountVO account = new AccountVO();
        account.setUuid("ACC-1");
        account.setProfileImgUrl("profiles/one.png");

        when(accountMapper.selectAccountByUuid("ACC-1")).thenReturn(account);
        when(fileStorageService.getStorageRoot()).thenReturn("storage-root");

        AccountDeletionExecutor executor = new AccountDeletionExecutor(
                accountMapper,
                fileStorageService,
                gameRecordMapper,
                rankingMapper,
                consentMapper,
                sharedGameRecordMapper,
                donationMapper,
                reportMapper
        );

        executor.deleteAccount("ACC-1");

        InOrder inOrder = inOrder(
                fileStorageService,
                donationMapper,
                reportMapper,
                sharedGameRecordMapper,
                gameRecordMapper,
                rankingMapper,
                consentMapper,
                accountMapper
        );
        inOrder.verify(fileStorageService).deleteFile("storage-root/profiles/one.png");
        inOrder.verify(donationMapper).unlinkAccountByAccountId("ACC-1");
        inOrder.verify(reportMapper).clearReporterAccountId("ACC-1");
        inOrder.verify(reportMapper).clearTargetAccountId("ACC-1");
        inOrder.verify(reportMapper).clearResolvedByAccountId("ACC-1");
        inOrder.verify(sharedGameRecordMapper).deleteAllByAccountId("ACC-1");
        inOrder.verify(gameRecordMapper).deleteAllByAccountId("ACC-1");
        inOrder.verify(rankingMapper).deleteAllByAccountId("ACC-1");
        inOrder.verify(consentMapper).deleteAllByAccountId("ACC-1");
        inOrder.verify(accountMapper).deleteAccount("ACC-1");
    }

    @Test
    void deleteAccount_throwsWhenAccountDoesNotExist() {
        when(accountMapper.selectAccountByUuid("ACC-404")).thenReturn(null);

        AccountDeletionExecutor executor = new AccountDeletionExecutor(
                accountMapper,
                fileStorageService,
                gameRecordMapper,
                rankingMapper,
                consentMapper,
                sharedGameRecordMapper,
                donationMapper,
                reportMapper
        );

        assertThatThrownBy(() -> executor.deleteAccount("ACC-404"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteAccount_ignoresProfileImageDeletionFailure() {
        AccountVO account = new AccountVO();
        account.setUuid("ACC-1");
        account.setProfileImgUrl("profiles/one.png");

        when(accountMapper.selectAccountByUuid("ACC-1")).thenReturn(account);
        when(fileStorageService.getStorageRoot()).thenReturn("storage-root");
        doThrow(new RuntimeException("delete failed")).when(fileStorageService).deleteFile("storage-root/profiles/one.png");

        AccountDeletionExecutor executor = new AccountDeletionExecutor(
                accountMapper,
                fileStorageService,
                gameRecordMapper,
                rankingMapper,
                consentMapper,
                sharedGameRecordMapper,
                donationMapper,
                reportMapper
        );

        executor.deleteAccount("ACC-1");

        verify(accountMapper).deleteAccount("ACC-1");
    }
}
