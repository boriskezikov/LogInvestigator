package smartbics.loginvestigator.logger;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import smartbics.loginvestigator.utils.LogNotFoundException;

import javax.annotation.PostConstruct;
import java.io.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static smartbics.loginvestigator.utils.Constants.logsPath;
import static smartbics.loginvestigator.utils.Constants.statisticsPath;

@Slf4j
@Component
@AllArgsConstructor
public class LogResolver {

    @PostConstruct
    public void init() throws IOException {
        File directory = new File(logsPath);
        if (directory.exists() && directory.isDirectory()) {
            processDirectory(directory);
        } else {
            log.error("Directory does not exist.");
        }
    }

    private static void processDirectory(File directory) throws IOException {
        List<LocalDateTime> timeList = Collections.synchronizedList(new LinkedList<>());

        File[] files = directory.listFiles();
        if (files == null) {
            log.error("No logs founds.");
            return;
        } else {
            log.info(String.format("Found %s log files: ", files.length));
        }

        ExecutorService service = Executors.newFixedThreadPool(10);
        Arrays.stream(files).filter(File::isFile).<Runnable>map(f -> () -> {
            try (Scanner scanner = new Scanner(f)) {
                while (scanner.hasNextLine()) {
                    parseDate(scanner.nextLine()).ifPresent(timeList::add);
                }
            } catch (FileNotFoundException e) {
                throw new LogNotFoundException("Log is absent in provided path.");
            }
        }).forEach(service::execute);
        service.shutdown();
        try {
            service.awaitTermination(3, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            log.error(e.getLocalizedMessage());
        }
        log.info("Log notes found: " + timeList.size());
        processLogsInfo(timeList);
    }

    private static Optional<LocalDateTime> parseDate(String logNote) {
        List<String> strings = Arrays.asList(logNote.split(";"));
        if (!strings.isEmpty()) {
            String date = strings.get(0);
            if (StringUtils.isNotBlank(date))
                return Optional.of(LocalDateTime.parse(date));
        }
        return Optional.empty();
    }

    private static void processLogsInfo(List<LocalDateTime> localDateTimes) throws IOException {
        FileWriter fileWriter = new FileWriter(statisticsPath);
        localDateTimes.stream()
                .collect(Collectors.groupingBy(e -> e.truncatedTo(ChronoUnit.MINUTES), Collectors.counting()))
                .entrySet().stream().sorted().forEach(k -> {
            try {
                fileWriter.write(String.format("From %s found %s log notes (errors/warnings). \n", k.getKey(), k.getValue()));
                log.info("New statistics added");
            } catch (IOException e) {
                log.error(e.getLocalizedMessage());
            }
        });
        fileWriter.close();
    }
}

