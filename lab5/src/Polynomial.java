import java.util.Random;

public class Polynomial {
    private final int[] coefficients;

    public Polynomial(int degree) {
        this.coefficients = new int[degree + 1];
    }

    public Polynomial(int[] coefficients) {
        this.coefficients = coefficients;
    }

    public static Polynomial generateRandom(int degree) {
        Polynomial p = new Polynomial(degree);
        Random r = new Random();
        for (int i = 0; i < p.coefficients.length; i++) {
            p.coefficients[i] = r.nextInt(10);
        }
        return p;
    }

    public int getLength() {
        return coefficients.length;
    }

    public int[] getCoeffs() {
        return coefficients;
    }

    public static Polynomial add(Polynomial p1, Polynomial p2) {
        int maxLen = Math.max(p1.getLength(), p2.getLength());
        int[] result = new int[maxLen];
        for (int i = 0; i < maxLen; i++) {
            int v1 = (i < p1.getLength()) ? p1.coefficients[i] : 0;
            int v2 = (i < p2.getLength()) ? p2.coefficients[i] : 0;
            result[i] = v1 + v2;
        }
        return new Polynomial(result);
    }

    public static Polynomial subtract(Polynomial p1, Polynomial p2) {
        int maxLen = Math.max(p1.getLength(), p2.getLength());
        int[] result = new int[maxLen];
        for (int i = 0; i < maxLen; i++) {
            int v1 = (i < p1.getLength()) ? p1.coefficients[i] : 0;
            int v2 = (i < p2.getLength()) ? p2.coefficients[i] : 0;
            result[i] = v1 - v2;
        }
        return new Polynomial(result);
    }

    public Polynomial shift(int offset) {
        int[] newCoeffs = new int[this.coefficients.length + offset];
        System.arraycopy(this.coefficients, 0, newCoeffs, offset, this.coefficients.length);
        return new Polynomial(newCoeffs);
    }
}