package com.bist.service;

import org.apache.commons.math3.stat.correlation.Covariance;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PortfolioOptimizer {

    public record OptimalPortfolio(Map<String, Double> weights, double risk) {}

    /**
     * Geçmiş getiri serilerine bakarak varyansı en aza indiren 
     * (Minimum Variance Portfolio) ağırlıkları Monte Carlo simülasyonuyla bulur.
     */
    public OptimalPortfolio optimize(List<String> symbols, Map<String, List<Double>> returnsMap) {
        int n = symbols.size();
        
        if (n == 1) {
            return new OptimalPortfolio(Map.of(symbols.get(0), 1.0), 0.0);
        }

        double[][] returnsMatrix = new double[n][];
        int numDays = returnsMap.get(symbols.get(0)).size();

        for (int i = 0; i < n; i++) {
            List<Double> r = returnsMap.get(symbols.get(i));
            returnsMatrix[i] = new double[numDays];
            for (int j = 0; j < numDays; j++) {
                returnsMatrix[i][j] = r.get(j);
            }
        }

        // Varyans-Kovaryans matrisinin hesaplanması (Apache Commons Math)
        double[][] covMatrix = new Covariance(transpose(returnsMatrix)).getCovarianceMatrix().getData();

        // Basit Monte Carlo yaklaşımı ile Minimum Risk portföyünü bulma
        int iterations = 50000;
        double minVar = Double.MAX_VALUE;
        double[] bestWeights = new double[n];

        Random rand = new Random();
        for (int i = 0; i < iterations; i++) {
            double[] w = new double[n];
            double sum = 0;
            for (int j = 0; j < n; j++) {
                w[j] = rand.nextDouble();
                sum += w[j];
            }
            double total = 0;
            for (int j = 0; j < n - 1; j++) {
                w[j] /= sum;
                total += w[j];
            }
            w[n - 1] = 1.0 - total; // Son ağırlık (1 - diğerleri) ile %100 toplam garantilenir.

            double var = calculatePortfolioVariance(w, covMatrix);
            if (var < minVar) {
                minVar = var;
                System.arraycopy(w, 0, bestWeights, 0, n);
            }
        }

        Map<String, Double> optimalWeights = new HashMap<>();
        for (int i = 0; i < n; i++) {
            optimalWeights.put(symbols.get(i), bestWeights[i]);
        }

        return new OptimalPortfolio(optimalWeights, Math.sqrt(minVar));
    }

    private double calculatePortfolioVariance(double[] weights, double[][] covMatrix) {
        double var = 0;
        int n = weights.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                var += weights[i] * weights[j] * covMatrix[i][j];
            }
        }
        return var;
    }

    private double[][] transpose(double[][] matrix) {
        int m = matrix.length;
        int n = matrix[0].length;
        double[][] transposed = new double[n][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                transposed[j][i] = matrix[i][j];
            }
        }
        return transposed;
    }
}
