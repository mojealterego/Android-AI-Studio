from datetime import datetime, timedelta, timezone
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from app.consent_core import Access, ConsentError, Duration, Grant
from app.consent_store import ConsentStore


class ConsentStoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.db_path = Path(self.temp.name) / "consent.sqlite3"
        self.store = ConsentStore(self.db_path)
        self.now = datetime.now(timezone.utc)

    def make_once(self):
        return Grant(
            subject_id="user-1",
            resource="workflow:image-generation",
            access=frozenset({Access.WRITE}),
            duration=Duration.ONCE,
            created_at=self.now,
            session_id="session-1",
            task_id="task-1",
        )

    def test_grant_survives_store_recreation(self):
        self.store.create("grant-1", self.make_once())
        reopened = ConsentStore(self.db_path)
        self.assertEqual(reopened.get("grant-1"), self.make_once())

    def test_once_grant_can_be_consumed_only_once(self):
        self.store.create("grant-1", self.make_once())
        kwargs = dict(
            subject_id="user-1",
            resource="workflow:image-generation",
            access=Access.WRITE,
            session_id="session-1",
            task_id="task-1",
            now=self.now,
        )
        self.store.authorize_and_consume_once("grant-1", **kwargs)
        with self.assertRaises(ConsentError):
            self.store.authorize_and_consume_once("grant-1", **kwargs)

    def test_wrong_resource_is_denied(self):
        self.store.create("grant-1", self.make_once())
        with self.assertRaises(ConsentError):
            self.store.authorize_and_consume_once(
                "grant-1",
                subject_id="user-1",
                resource="workflow:video-generation",
                access=Access.WRITE,
                session_id="session-1",
                task_id="task-1",
                now=self.now,
            )

    def test_revocation_blocks_use(self):
        self.store.create("grant-1", self.make_once())
        self.assertTrue(self.store.revoke("grant-1", now=self.now + timedelta(seconds=1)))
        with self.assertRaises(ConsentError):
            self.store.authorize_and_consume_once(
                "grant-1",
                subject_id="user-1",
                resource="workflow:image-generation",
                access=Access.WRITE,
                session_id="session-1",
                task_id="task-1",
                now=self.now + timedelta(seconds=2),
            )

    def test_unknown_grant_fails_closed(self):
        with self.assertRaises(ConsentError):
            self.store.authorize_and_consume_once(
                "missing",
                subject_id="user-1",
                resource="workflow:image-generation",
                access=Access.WRITE,
                session_id="session-1",
                task_id="task-1",
                now=self.now,
            )


if __name__ == "__main__":
    unittest.main()
