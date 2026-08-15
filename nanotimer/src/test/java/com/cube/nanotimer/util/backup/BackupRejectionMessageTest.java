package com.cube.nanotimer.util.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cube.nanotimer.R;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * A refused file has to say which kind of file it is. The switch that decides that ends in a
 * default, so a reason added later would silently borrow "this is not a backup" instead of getting
 * a message of its own. That is what this is for.
 */
public class BackupRejectionMessageTest {

  @Test
  public void everyReasonHasAMessage() {
    for (BackupRejection rejection : BackupRejection.values()) {
      assertTrue(rejection.name(), BackupRestorer.messageFor(rejection) != 0);
    }
  }

  /** Two reasons sharing a message means one of them fell through the default without a string. */
  @Test
  public void noTwoReasonsShareAMessage() {
    Set<Integer> messages = new HashSet<Integer>();
    for (BackupRejection rejection : BackupRejection.values()) {
      assertTrue(rejection.name(), messages.add(BackupRestorer.messageFor(rejection)));
    }
  }

  /** The default branch is the not-a-backup one, so it has to be that message and not a stray. */
  @Test
  public void theDefaultBranchIsTheOneItClaimsToBe() {
    assertEquals(R.string.restore_rejected_not_a_backup,
      BackupRestorer.messageFor(BackupRejection.NOT_A_BACKUP));
  }

}
