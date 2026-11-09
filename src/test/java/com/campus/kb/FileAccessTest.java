package com.campus.kb;

import com.campus.kb.auth.UserPrincipal;
import com.campus.kb.file.FileService;
import com.campus.kb.file.KnowledgeFile;
import com.campus.kb.file.Visibility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileAccessTest {

    private final UserPrincipal owner = new UserPrincipal(2L, "alice", "Alice", "STUDENT");
    private final UserPrincipal other = new UserPrincipal(3L, "bob", "Bob", "STUDENT");
    private final UserPrincipal admin = new UserPrincipal(1L, "admin", "管理员", "ADMIN");

    @Test
    void privateFileIsVisibleOnlyToOwnerEvenForAdmin() {
        KnowledgeFile file = new KnowledgeFile();
        file.setOwnerId(owner.id());
        file.setVisibility(Visibility.PRIVATE);

        assertThat(FileService.canView(file, owner)).isTrue();
        assertThat(FileService.canView(file, other)).isFalse();
        assertThat(FileService.canView(file, admin)).isFalse();
        assertThat(FileService.canManage(file, admin)).isTrue();
    }

    @Test
    void publicFileIsVisibleToEveryone() {
        KnowledgeFile file = new KnowledgeFile();
        file.setOwnerId(admin.id());
        file.setVisibility(Visibility.PUBLIC);

        assertThat(FileService.canView(file, other)).isTrue();
        assertThat(FileService.canView(file, admin)).isTrue();
    }
}