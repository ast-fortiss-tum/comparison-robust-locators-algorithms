import sys, joblib
m = joblib.load(sys.argv[1])
print(type(m), getattr(m, "classes_", None), getattr(m, "n_features_in_", None))
print({k: v for k, v in m.get_params().items() if k != "estimator"} if hasattr(m, "get_params") else "")
ests = getattr(m, "estimators_", [])
print("trees", len(ests), "nodes", sum(e.tree_.node_count for e in ests), "max_depth", max(e.tree_.max_depth for e in ests) if ests else None)
if ests: print("value shape", ests[0].tree_.value.shape, ests[0].tree_.value[0])
