# Minimal, isolated runtime for EAGL's Python implementation (versions pinned as in hilalosoft/EAGL requirements.txt).
# The EAGL checkout, the bridge script and the sites data are mounted read-only at run time; see scripts/eagl/run.sh.
FROM python:3.10.14-slim
RUN pip install --no-cache-dir "beautifulsoup4==4.11.1" "joblib==1.2.0" "lxml==4.9.1" "numpy==1.23.5" \
        "scipy==1.10.1" "scikit-learn==1.2.1" "pandas==1.4.3" "selenium==4.5.0" "timeout-decorator==0.5.0"
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /work
ENTRYPOINT ["python", "scripts/eagl_generate.py"]
