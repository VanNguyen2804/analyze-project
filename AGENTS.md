# Project requirements

- Preserve all existing functionality when making changes.
- After each completed change, successfully build both frontend (`npm run build` in `frontend`) and backend (`mvn verify` in `backend`).
- Run the frontend regression suite (`npm run test:ci`) and checks relevant to the changed behavior. Investigate failures before declaring the work complete.
- Report actual build/test outcomes and explicitly identify any behavior not verified in the running deployment. Do not claim that passing builds alone proves every feature works.
