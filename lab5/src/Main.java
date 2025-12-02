import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws Exception {
        int degree = 20000;
        System.out.println("Generating polynomials of degree: " + degree);

        Polynomial p1 = Polynomial.generateRandom(degree);
        Polynomial p2 = Polynomial.generateRandom(degree);

        System.out.println("Starting Benchmark...\n");

        long start = System.nanoTime();
        Polynomial res1 = Multiplication.multiplySequentialRegular(p1, p2);
        long end = System.nanoTime();
        System.out.println("Sequential Regular: " + (end - start) / 1000000.0 + " ms");

        start = System.nanoTime();
        Polynomial res2 = Multiplication.multiplyParallelRegular(p1, p2);
        end = System.nanoTime();
        System.out.println("Parallel Regular:   " + (end - start) / 1000000.0 + " ms");

        start = System.nanoTime();
        Polynomial res3 = Multiplication.multiplySequentialKaratsuba(p1, p2);
        end = System.nanoTime();
        System.out.println("Seq. Karatsuba:     " + (end - start) / 1000000.0 + " ms");

        start = System.nanoTime();
        Polynomial res4 = Multiplication.multiplyParallelKaratsuba(p1, p2, 0);
        end = System.nanoTime();
        System.out.println("Parallel Karatsuba: " + (end - start) / 1000000.0 + " ms");

        boolean valid = Arrays.equals(res1.getCoeffs(), res2.getCoeffs()) &&
                Arrays.equals(res1.getCoeffs(), res3.getCoeffs()) &&
                Arrays.equals(res1.getCoeffs(), res4.getCoeffs());

        System.out.println("\nVerification: " + (valid ? "PASSED" : "FAILED"));
    }
}