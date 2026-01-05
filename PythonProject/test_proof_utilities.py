import os
import json
import unittest
import uuid
from proof_utilities import HardeningProof
from execution_mode import ExecutionMode

class TestProofUtilities(unittest.TestCase):
    def setUp(self):
        self.log_path = "test_audit.json"
        # Set execution mode for proof utility
        os.environ["AISERVER_MODE"] = ExecutionMode.AUDIT_PROOF.value

    def tearDown(self):
        if os.path.exists(self.log_path):
            os.remove(self.log_path)

    def test_hardening_proof(self):
        # Create a mock log with hardening fields
        logs = [
            {
                "experience_id": str(uuid.uuid4()),
                "authority": "AUTHORITATIVE",
                "reward_class": "LEARNING_APPLICABLE",
                "lineage": {
                    "source": "JAVA_SOURCE",
                    "trust_boundary": "INTERNAL_VERIFIED",
                    "learning_allowed": True,
                    "decision_authority": "ML_MODEL"
                }
            },
            {
                "experience_id": str(uuid.uuid4()),
                "authority": "OVERRIDE",
                "reward_class": "DIAGNOSTIC",
                "lineage": {
                    "source": "JAVA_SOURCE",
                    "trust_boundary": "EXTERNAL_UNTRUSTED",
                    "learning_allowed": False,
                    "decision_authority": "HEURISTIC"
                }
            }
        ]
        with open(self.log_path, 'w') as f:
            json.dump(logs, f)

        proof = HardeningProof(self.log_path)
        self.assertTrue(proof.verify_all_experience_ids())
        self.assertTrue(proof.verify_trust_boundaries())
        self.assertTrue(proof.verify_policy_authority())
        self.assertTrue(proof.verify_reward_classification())

    def test_bad_reward_classification_proof(self):
        # learning_allowed = False but reward_class = LEARNING_APPLICABLE
        logs = [{
            "experience_id": str(uuid.uuid4()),
            "authority": "AUTHORITATIVE",
            "reward_class": "LEARNING_APPLICABLE",
            "lineage": {
                "source": "JAVA_SOURCE",
                "trust_boundary": "EXTERNAL_UNTRUSTED",
                "learning_allowed": False, # VIOLATION
                "decision_authority": "ML_MODEL"
            }
        }]
        with open(self.log_path, 'w') as f:
            json.dump(logs, f)

        proof = HardeningProof(self.log_path)
        self.assertFalse(proof.verify_reward_classification())

if __name__ == "__main__":
    unittest.main()
