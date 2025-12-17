#include "Polynomial.h"
#include <random>

Poly generateRandomPoly(int degree) {
    Poly coeffs(degree + 1);
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> distrib(0, 9);

    for (int& coeff : coeffs) {
        coeff = distrib(gen);
    }
    return coeffs;
}

Poly polyAdd(const Poly& p1, const Poly& p2) {
    int maxLen = std::max(p1.size(), p2.size());
    Poly result(maxLen);
    for (int i = 0; i < maxLen; i++) {
        int v1 = (i < p1.size()) ? p1[i] : 0;
        int v2 = (i < p2.size()) ? p2[i] : 0;
        result[i] = v1 + v2;
    }
    return result;
}

Poly polySubtract(const Poly& p1, const Poly& p2) {
    int maxLen = std::max(p1.size(), p2.size());
    Poly result(maxLen);
    for (int i = 0; i < maxLen; i++) {
        int v1 = (i < p1.size()) ? p1[i] : 0;
        int v2 = (i < p2.size()) ? p2[i] : 0;
        result[i] = v1 - v2;
    }
    return result;
}

Poly polyShift(const Poly& p, int offset) {
    if (p.empty()) return {};
    Poly newCoeffs(p.size() + offset, 0);
    std::copy(p.begin(), p.end(), newCoeffs.begin() + offset);
    return newCoeffs;
}