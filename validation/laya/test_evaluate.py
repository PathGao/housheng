import unittest

from evaluate import percentile, summarize


class MetricsTest(unittest.TestCase):
    def test_false_filter_includes_unknown_but_not_missed_filter(self):
        pairs = [("keep", "filter"), ("uncertain", "filter"),
                 ("filter", "keep"), ("filter", "filter"), ("keep", "keep")]
        rows = [dict(id=str(i), expected=a, predicted=b, correct=a == b,
                     elapsed_ms=i + 1, answer_probability=.95)
                for i, (a, b) in enumerate(pairs)]
        result = summarize(rows)
        self.assertEqual(result["false_filter_count"], 2)
        self.assertEqual(result["non_filter_count"], 3)
        self.assertEqual(result["accuracy"], .4)
        self.assertEqual(result["filter_recall"], .5)
        self.assertEqual(result["wrong_at_probability_0_9"], ["0", "1", "2"])

    def test_tail_latency_is_not_average(self):
        values = list(range(1, 101))
        self.assertEqual(percentile(values, .95), 95)
        self.assertEqual(percentile([500, 20, 30], .95), 500)


if __name__ == "__main__":
    unittest.main()
