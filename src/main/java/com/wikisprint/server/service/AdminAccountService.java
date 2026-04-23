package com.wikisprint.server.service;

import com.wikisprint.server.dto.AdminAccountListRequestDTO;
import com.wikisprint.server.dto.AdminAccountListResponseDTO;
import com.wikisprint.server.dto.AdminAccountResponseDTO;
import com.wikisprint.server.mapper.AccountMapper;
import com.wikisprint.server.vo.AccountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final String VIEW_REPORTED = "REPORTED";
    private static final String VIEW_ALL = "ALL";
    private static final String SORT_RECENT_LOGIN = "RECENT_LOGIN";
    private static final String SORT_RECENT_JOIN = "RECENT_JOIN";
    private static final String SORT_NAME = "NAME";
    private static final String DIRECTION_ASC = "ASC";
    private static final String DIRECTION_DESC = "DESC";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

    private final AccountMapper accountMapper;
    private final ReportService reportService;

    @Transactional(readOnly = true)
    public AdminAccountListResponseDTO getAccounts(AdminAccountListRequestDTO request) {
        AdminAccountListRequestDTO safeRequest = request == null ? new AdminAccountListRequestDTO() : request;
        String view = normalizeView(safeRequest.getView());
        String sortColumn = resolveSortColumn(safeRequest.getSort());
        String direction = normalizeDirection(safeRequest.getDirection());
        String search = normalizeSearch(safeRequest.getSearch());
        int page = normalizePage(safeRequest.getPage());
        int size = normalizeSize(safeRequest.getSize());
        int offset = (page - 1) * size;

        long totalCount = accountMapper.countAdminAccounts(view, search);
        List<AdminAccountResponseDTO> accounts = accountMapper.selectAdminAccounts(
                        view,
                        sortColumn,
                        direction,
                        search,
                        size,
                        offset
                )
                .stream()
                .map(this::toResponse)
                .toList();

        int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / size);
        return AdminAccountListResponseDTO.builder()
                .accounts(accounts)
                .page(page)
                .size(size)
                .totalPages(totalPages)
                .totalCount(totalCount)
                .build();
    }

    private AdminAccountResponseDTO toResponse(AccountVO account) {
        int pendingReportCount = reportService.countPendingReportsByTarget(
                ReportService.TARGET_ACCOUNT,
                account.getUuid()
        );

        return AdminAccountResponseDTO.builder()
                .accountId(account.getUuid())
                .email(account.getEmail())
                .nick(account.getNick())
                .profileImgUrl(account.getProfileImgUrl())
                .nationality(account.getNationality())
                .isAdmin(account.getIsAdmin())
                .lastLogin(account.getLastLogin())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .totalGames(account.getTotalGames())
                .totalClears(account.getTotalClears())
                .totalAbandons(account.getTotalAbandons())
                .bestRecord(account.getBestRecord())
                .deletionRequestedAt(account.getDeletionRequestedAt())
                .pendingReportCount(pendingReportCount)
                .build();
    }

    private String normalizeView(String view) {
        if (VIEW_ALL.equals(view)) {
            return VIEW_ALL;
        }
        return VIEW_REPORTED;
    }

    private String resolveSortColumn(String sort) {
        if (SORT_NAME.equals(sort)) {
            return "a.nick";
        }
        if (SORT_RECENT_JOIN.equals(sort)) {
            return "a.created_at";
        }
        return "a.last_login";
    }

    private String normalizeDirection(String direction) {
        if (DIRECTION_ASC.equals(direction)) {
            return DIRECTION_ASC;
        }
        return DIRECTION_DESC;
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
