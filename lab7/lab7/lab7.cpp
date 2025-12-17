#include <iostream>
#include <vector>
#include <algorithm>
#include <random>
#include <chrono>
#include <mpi.h>

using Poly = std::vector<int>;
using namespace std::chrono;

Poly generateRandomPoly(int degree) {
    Poly coeffs(degree + 1);
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> distrib(0, 9);
    for (int& coeff : coeffs) coeff = distrib(gen);
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

void removeLeadingZeros(Poly& p) {
    while (p.size() > 1 && p.back() == 0) {
        p.pop_back();
    }
}

int calculateCoefficient(const Poly& A, const Poly& B, int k) {
    int N = A.size();
    int M = B.size();
    long long result_coeff = 0;
    int start_i = std::max(0, k - M + 1);
    int end_i = std::min(k, N - 1);
    for (int i = start_i; i <= end_i; ++i) {
        result_coeff += (long long)A[i] * B[k - i];
    }
    return (int)result_coeff;
}

Poly multiplySequentialRegular_Helper(const Poly& p1, const Poly& p2) {
    int size1 = p1.size();
    int size2 = p2.size();
    Poly result(size1 + size2 - 1, 0);
    for (int i = 0; i < size1; i++) {
        for (int j = 0; j < size2; j++) {
            result[i + j] += p1[i] * p2[j];
        }
    }
    return result;
}

Poly multiplySequentialKaratsuba_Helper(const Poly& p1_in, const Poly& p2_in) {
    Poly p1 = p1_in;
    Poly p2 = p2_in;

    if (p1.size() < 64 || p2.size() < 64) {
        return multiplySequentialRegular_Helper(p1, p2);
    }

    int n = std::max(p1.size(), p2.size());
    int len = n / 2;

    if (p1.size() < n) p1.resize(n, 0);
    if (p2.size() < n) p2.resize(n, 0);

    Poly low1(p1.begin(), p1.begin() + len);
    Poly high1(p1.begin() + len, p1.end());
    Poly low2(p2.begin(), p2.begin() + len);
    Poly high2(p2.begin() + len, p2.end());

    Poly z0 = multiplySequentialKaratsuba_Helper(low1, low2);
    Poly z2 = multiplySequentialKaratsuba_Helper(high1, high2);
    Poly z1 = multiplySequentialKaratsuba_Helper(polyAdd(low1, high1), polyAdd(low2, high2));

    Poly middle = polySubtract(polySubtract(z1, z0), z2);
    Poly r1 = polyShift(z2, 2 * len);
    Poly r2 = polyShift(middle, len);

    return polyAdd(polyAdd(r1, r2), z0);
}

void sendPoly(const Poly& p, int dest, int tag) {
    int size = p.size();
    MPI_Send(&size, 1, MPI_INT, dest, tag, MPI_COMM_WORLD);
    MPI_Send(p.data(), size, MPI_INT, dest, tag + 1, MPI_COMM_WORLD);
}

Poly recvPoly(int source, int tag) {
    int size;
    MPI_Recv(&size, 1, MPI_INT, source, tag, MPI_COMM_WORLD, MPI_STATUS_IGNORE);
    Poly p(size);
    MPI_Recv(p.data(), size, MPI_INT, source, tag + 1, MPI_COMM_WORLD, MPI_STATUS_IGNORE);
    return p;
}

Poly multiplyParallelRegularMPI(const Poly& p1, const Poly& p2, int rank, int size) {
    int N = p1.size();
    int M = p2.size();
    int result_size = N + M - 1;

    int chunk_size = result_size / size;
    int remainder = result_size % size;

    int start_k = rank * chunk_size + std::min(rank, remainder);
    int end_k = start_k + chunk_size + (rank < remainder ? 1 : 0);
    int local_n = end_k - start_k;

    Poly local_result(local_n);
    for (int k = start_k; k < end_k; ++k) {
        local_result[k - start_k] = calculateCoefficient(p1, p2, k);
    }

    Poly final_result;
    std::vector<int> recvcounts;
    std::vector<int> displs;

    if (rank == 0) {
        final_result.resize(result_size);
        recvcounts.resize(size);
        displs.resize(size);
        int current_disp = 0;
        for (int i = 0; i < size; ++i) {
            int i_local_n = (result_size / size) + (i < (result_size % size) ? 1 : 0);
            recvcounts[i] = i_local_n;
            displs[i] = current_disp;
            current_disp += i_local_n;
        }
    }

    MPI_Gatherv(local_result.data(), local_n, MPI_INT,
        rank == 0 ? final_result.data() : nullptr,
        recvcounts.data(), displs.data(), MPI_INT,
        0, MPI_COMM_WORLD);

    return final_result;
}

Poly multiplyParallelKaratsubaMPI(Poly p1, Poly p2, int rank, int size) {
    if (size < 4) {
        if (rank == 0) return multiplySequentialKaratsuba_Helper(p1, p2);
        return {};
    }

    if (rank == 0) {
        int n = std::max(p1.size(), p2.size());
        int len = n / 2;

        if (p1.size() < n) p1.resize(n, 0);
        if (p2.size() < n) p2.resize(n, 0);

        Poly low1(p1.begin(), p1.begin() + len);
        Poly high1(p1.begin() + len, p1.end());
        Poly low2(p2.begin(), p2.begin() + len);
        Poly high2(p2.begin() + len, p2.end());

        Poly sum_l = polyAdd(low1, high1);
        Poly sum_h = polyAdd(low2, high2);

        sendPoly(low1, 1, 10); sendPoly(low2, 1, 12);

        sendPoly(high1, 2, 20); sendPoly(high2, 2, 22);

        sendPoly(sum_l, 3, 30); sendPoly(sum_h, 3, 32);

        Poly z0 = recvPoly(1, 100);
        Poly z2 = recvPoly(2, 200);
        Poly z1 = recvPoly(3, 300);

        Poly middle = polySubtract(polySubtract(z1, z0), z2);
        Poly r1 = polyShift(z2, 2 * len);
        Poly r2 = polyShift(middle, len);

        Poly res = polyAdd(polyAdd(r1, r2), z0);
        removeLeadingZeros(res);
        return res;
    }

    else if (rank <= 3) {
        Poly partA = recvPoly(0, rank * 10);
        Poly partB = recvPoly(0, rank * 10 + 2);

        Poly result = multiplySequentialKaratsuba_Helper(partA, partB);

        sendPoly(result, 0, rank * 100);
        return {};
    }

    return {};
}

int main(int argc, char** argv) {
    MPI_Init(&argc, &argv);
    int rank, size;
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    MPI_Comm_size(MPI_COMM_WORLD, &size);

    int degree = 2000;
    Poly p1, p2;
    int N = 0, M = 0;

    if (rank == 0) {
        std::cout << "Polynomial degree: " << degree << std::endl;
        p1 = generateRandomPoly(degree);
        p2 = generateRandomPoly(degree);
        N = p1.size();
        M = p2.size();
    }

    MPI_Bcast(&N, 1, MPI_INT, 0, MPI_COMM_WORLD);
    MPI_Bcast(&M, 1, MPI_INT, 0, MPI_COMM_WORLD);
    if (rank != 0) { p1.resize(N); p2.resize(M); }
    MPI_Bcast(p1.data(), N, MPI_INT, 0, MPI_COMM_WORLD);
    MPI_Bcast(p2.data(), M, MPI_INT, 0, MPI_COMM_WORLD);


    MPI_Barrier(MPI_COMM_WORLD);
    auto start_reg = high_resolution_clock::now();
    Poly res_mpi_reg = multiplyParallelRegularMPI(p1, p2, rank, size);
    auto end_reg = high_resolution_clock::now();

    if (rank == 0) {
        std::cout << "Distributed MPI Regular:   "
            << duration_cast<milliseconds>(end_reg - start_reg).count() / 1000.0 << " s" << std::endl;
    }

    MPI_Barrier(MPI_COMM_WORLD);
    auto start_kara = high_resolution_clock::now();
    Poly res_mpi_kara = multiplyParallelKaratsubaMPI(p1, p2, rank, size);
    auto end_kara = high_resolution_clock::now();

    if (rank == 0) {
        std::cout << "Distributed MPI Karatsuba: "
            << duration_cast<milliseconds>(end_kara - start_kara).count() / 1000.0 << " s" << std::endl;
    }

    MPI_Finalize();
    return 0;
}