# Features

## Cheapest energy providers by vehicle type

### Fuel vehicle

**As a** user with a fuel car,  
**I want** to find the cheapest fuel provider around me,  
**so that** I can refuel at the best available price near my location.

---

### Electric vehicle

**As a** user with an electric car,  
**I want** to find the cheapest electric provider around me,  
**so that** I can charge at the best available price near my location.

---

### Hybrid vehicle

**As a** user with a hybrid car,  
**I want** to find the cheapest fuel and electric providers around me,  
**so that** I can choose the most economical option for both fuel and charging near my location.

---

## Driving assistant (France — Level A)

**As a** driver in France,  
**I want** optional danger-zone alerts with posted speed limits,  
**so that** I get an aide à la conduite without precise enforcement-control locations.

- Opt-in (default **off**); Play builds gated by `AAC_ALERTS_AVAILABLE` / kill switch.
- See [`docs/RADARS_NF469_TODO_NIVEAU_A.md`](RADARS_NF469_TODO_NIVEAU_A.md), [`docs/AAC_ARCHITECTURE.md`](AAC_ARCHITECTURE.md).

---

## Summary

| Vehicle type | Goal |
|--------------|------|
| Fuel | Cheapest **fuel** provider nearby |
| Electric | Cheapest **electric** (charging) provider nearby |
| Hybrid | Cheapest **fuel + electric** providers nearby |
