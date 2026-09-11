package com.rightpath.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.rightpath.entity.Users;
import com.rightpath.rbac.RoleName;

/**
 * Exercises {@link UsersRepository#findAllByActiveRoleIn} against the real
 * MySQL schema.
 *
 * <p>Two properties matter and neither is visible by reading the JPQL: that the
 * staff list and the candidate list are complements of each other (nobody
 * missing, nobody on both), and that a revoked assignment does not keep someone
 * on the staff list. Read-only, so it is safe against a shared dev database.</p>
 */
@DataJpaTest
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StaffUserLookupTest {

    private static final List<RoleName> STAFF = List.of(RoleName.SUPER_ADMIN, RoleName.ADMIN);

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void returnsNothingForAnEmptyRoleSet() {
        assertTrue(usersRepository.findAllByActiveRoleIn(List.of()).isEmpty());
    }

    @Test
    void everyStaffUserActuallyHoldsAStaffRole() {
        List<Users> staff = usersRepository.findAllByActiveRoleIn(STAFF);

        for (Users user : staff) {
            Set<RoleName> held = userRoleRepository.findByUser_EmailAndActiveTrue(user.getEmail()).stream()
                    .map(assignment -> assignment.getRole().getName())
                    .collect(Collectors.toSet());
            assertTrue(
                    held.contains(RoleName.ADMIN) || held.contains(RoleName.SUPER_ADMIN),
                    () -> user.getEmail() + " is listed as staff but holds " + held);
        }
    }

    @Test
    void listsEachStaffUserExactlyOnce() {
        List<Users> staff = usersRepository.findAllByActiveRoleIn(STAFF);
        Set<String> emails = staff.stream().map(Users::getEmail).collect(Collectors.toSet());

        // Someone holding both ADMIN and SUPER_ADMIN must not appear twice.
        assertEquals(emails.size(), staff.size(), "the staff list contains a duplicate");
    }

    @Test
    void staffAndCandidateListsDoNotOverlap() {
        // The admin screen and the candidate screen are built on these two
        // queries, so an account on both would be administered from two places.
        // This caught exactly that: excluding only ADMIN left super admins on the
        // candidate list as well.
        Set<String> staff = usersRepository.findAllByActiveRoleIn(STAFF).stream()
                .map(Users::getEmail).collect(Collectors.toSet());
        Set<String> candidates = usersRepository.findAllExcludingActiveRoleIn(STAFF).stream()
                .map(Users::getEmail).collect(Collectors.toSet());

        Set<String> onBoth = staff.stream().filter(candidates::contains).collect(Collectors.toSet());
        assertTrue(onBoth.isEmpty(), () -> "accounts on both the staff and candidate lists: " + onBoth);
    }
}
