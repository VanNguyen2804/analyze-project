package com.example.analyzeproject.service;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

@Service
public class AnalyzeService {
    public List<Integer> predictNumbers() {
        try {
            // Gọi kịch bản Python XGBoost
            ProcessBuilder pb = new ProcessBuilder("python", "xgboost_predict.py");
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String jsonOutput = reader.readLine(); 
            
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String error = errorReader.readLine();
            if (error != null && !error.isEmpty()) {
                System.out.println("Python Error: " + error);
            }

            ObjectMapper mapper = new ObjectMapper();
            if (jsonOutput != null) {
                return mapper.readValue(jsonOutput, new TypeReference<List<Integer>>(){});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fallbackRandomPredict();
    }

    private List<Integer> fallbackRandomPredict() {
        Set<Integer> numbers = new HashSet<>();
        Random rand = new Random();
        while(numbers.size() < 6) {
            numbers.add(rand.nextInt(45) + 1);
        }
        List<Integer> result = new ArrayList<>(numbers);
        Collections.sort(result);
        return result;
    }
}
