import unittest
import time
import math
from dataset import DatasetPipeline
from intent_space import Intent

class TestDatasetPipeline(unittest.TestCase):

    def setUp(self):
        self.good_experience = {
            "timestamp": time.time(),
            "state": {
                "version": 1,
                "normalized": {
                    "health": 0.8,
                    "energy": 0.5,
                    "target_distance": 0.2,
                    "is_colliding": False
                }
            },
            "intent": Intent.MOVE.value,
            "reward": 0.5,
            "controller": "AI",
            "state_version": 1
        }

    def test_vectorization(self):
        processed = DatasetPipeline.process_experiences([self.good_experience])
        self.assertEqual(len(processed), 1)
        features = processed[0]["features"]
        # Expected: [health, energy, target_distance, is_colliding]
        self.assertEqual(features, [0.8, 0.5, 0.2, 0.0])
        self.assertEqual(processed[0]["label"], DatasetPipeline.INTENT_TO_IDX[Intent.MOVE.value])

    def test_filtering_invalid_reward(self):
        bad_exp = self.good_experience.copy()
        bad_exp["reward"] = 5.0 # Out of bounds [-2, 2]
        processed = DatasetPipeline.process_experiences([bad_exp])
        self.assertEqual(len(processed), 0)

    def test_filtering_nan_features(self):
        bad_exp = self.good_experience.copy()
        bad_exp["state"] = self.good_experience["state"].copy()
        bad_exp["state"]["normalized"] = self.good_experience["state"]["normalized"].copy()
        bad_exp["state"]["normalized"]["health"] = float('nan')
        processed = DatasetPipeline.process_experiences([bad_exp])
        self.assertEqual(len(processed), 0)

    def test_filtering_invalid_intent(self):
        bad_exp = self.good_experience.copy()
        bad_exp["intent"] = "DANCE_BATTLE"
        processed = DatasetPipeline.process_experiences([bad_exp])
        self.assertEqual(len(processed), 0)

    def test_temporal_split_anti_leakage(self):
        experiences = []
        # 10 episodes, each with 2 samples
        for eid in range(10):
            for i in range(2):
                exp = self.good_experience.copy()
                exp["timestamp"] = eid * 10 + i
                exp["episode_id"] = eid
                experiences.append(exp)
        
        processed = DatasetPipeline.process_experiences(experiences)
        splits = DatasetPipeline.split(processed, train_ratio=0.6, val_ratio=0.2)
        
        # 6 episodes in train = 12 samples
        self.assertEqual(len(splits["train"]), 12)
        # 2 episodes in val = 4 samples
        self.assertEqual(len(splits["val"]), 4)
        # 2 episodes in test = 4 samples
        self.assertEqual(len(splits["test"]), 4)
        
        # Verify no episode leakage
        train_eids = set(d["metadata"]["episode_id"] for d in splits["train"])
        val_eids = set(d["metadata"]["episode_id"] for d in splits["val"])
        test_eids = set(d["metadata"]["episode_id"] for d in splits["test"])
        
        self.assertTrue(train_eids.isdisjoint(val_eids))
        self.assertTrue(val_eids.isdisjoint(test_eids))
        self.assertTrue(train_eids.isdisjoint(test_eids))

    def test_metrics_imbalance(self):
        # 10 MOVE intents (100% imbalance)
        experiences = [self.good_experience] * 10
        processed = DatasetPipeline.process_experiences(experiences)
        with self.assertLogs('dataset', level='WARNING') as cm:
            metrics = DatasetPipeline.get_metrics(processed)
            self.assertTrue(any("imbalance detected" in output for output in cm.output))
        
        self.assertEqual(metrics["intent_distribution"][Intent.MOVE.value], 1.0)

if __name__ == '__main__':
    unittest.main()
