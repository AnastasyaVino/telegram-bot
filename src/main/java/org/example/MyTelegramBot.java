package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("ALL")
public class MyTelegramBot extends TelegramLongPollingBot {
private final Map<Long, ScheduledFuture<?>> reminders = new HashMap<>();
private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    // Состояния пользователя
    public enum UserState {
        AWAITING_GENDER,
        AWAITING_HEIGHT,
        AWAITING_WEIGHT
    }

    // Хранилище состояний пользователей
    private final Map<Long, UserState> userStates = new HashMap<>();
    private final Map<Long, String> userGenders = new HashMap<>();
    private final Map<Long, Double> userHeights = new HashMap<>();
    private final Database dbHandler = new Database();

    @Override
    public void onUpdateReceived(@NotNull Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            //    String memberName = update.getMessage().getFrom().getFirstName();
            // Обработка команд
            switch (messageText) {
                case "/start":
                    sendStartMessage(chatId);
                    break;
                case "/imt":
                    requestGender(chatId);
                    break;
                case "/history":
                    sendUserHistory(chatId);
                    break;
                case "/reminder_on":
                    enableReminder(chatId);
                    break;
                case "/reminder_off":
                    disableReminder(chatId);
                    break;
                default:
                    handleUserInput(chatId, messageText);
            }
        }else if (update.hasCallbackQuery()) {
            // Обработка выбора пола
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            switch (callbackData) {
                case "gender_male":
                    userGenders.put(chatId, "мужской");
                    userStates.put(chatId, UserState.AWAITING_HEIGHT);
                    requestHeight(chatId);
                    break;
                    case "gender_female":
                        userGenders.put(chatId, "женский");
                        userStates.put(chatId, UserState.AWAITING_HEIGHT);
                        requestHeight(chatId);
                        break;
                        default:
                            sendMessage(chatId, "Неизвестный выбор. Попробуйте снова.");
            }
        }
    }


    private void sendStartMessage(long chatId) {
        sendMessage(chatId, """
                Привет! Я бот для расчета индекса массы тела (ИМТ).
                Выберите одну из опций:
                /imt - Рассчитать ИМТ
                /history - Просмотреть историю расчетов
                /reminder_on - Включить напоминание, о измерении ИМТ
                /reminder_off - Выключить напоминание, о измерении ИМТ
                """);
    }

    private void sendGenderSelection(long chatId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопка "Мужской"
        InlineKeyboardButton maleButton = new InlineKeyboardButton("Мужской");
        maleButton.setCallbackData("gender_male");

        // Кнопка "Женский"
        InlineKeyboardButton femaleButton = new InlineKeyboardButton("Женский");
        femaleButton.setCallbackData("gender_female");

        // Добавляем кнопки в одну строку
        rows.add(Arrays.asList(maleButton, femaleButton));
        keyboard.setKeyboard(rows);

        // Отправляем сообщение с клавиатурой
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Выберите ваш пол:");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestGender(long chatId) {
        sendGenderSelection(chatId);
        userStates.put(chatId, UserState.AWAITING_GENDER);
    }

    private void sendUserHistory(long chatId) {
        String history = dbHandler.getUserHistory(String.valueOf(chatId));
        if (history == null || history.isEmpty()){
            sendMessage(chatId, "Нет истории расчетов((.");
        } else {
        sendMessage(chatId, history);
    }
    }

    private void handleUserInput(long chatId, String messageText) {
        UserState currentState = userStates.get(chatId);

        if (currentState == null) {
            sendMessage(chatId, "Неизвестная команда. Введите /start для справки.");
            return;
        }

        switch (currentState) {
            case AWAITING_HEIGHT:
                try {
                    double height = Double.parseDouble(messageText);
                    if (height > 62 && height < 273) {
                        userHeights.put(chatId, height);
                        userStates.put(chatId, UserState.AWAITING_WEIGHT);
                        requestWeight(chatId);
                    } else {
                        sendMessage(chatId, "Рост должен быть больше 62см и меньше 273см. Попробуйте снова:");
                    }
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Ошибка: Пожалуйста, введите корректное число для роста:");
                }
                break;
            case AWAITING_WEIGHT:
                try {
                    double weight = Double.parseDouble(messageText);
                    if (weight > 27 && weight < 611) {
                        double height = userHeights.get(chatId);
                        String gender = userGenders.get(chatId);
                        double bmi = calculateBMI(weight, height);
                        String interpretation = interpretBMI(bmi, gender);
                        String recommendations = getRecommendations(bmi, gender);

                        sendMessage(chatId, String.format("""
                                Ваш ИМТ: %.2f
                                Категория: %s
                                Рекомендации: %s
                                /history - Просмотреть историю расчетов
                                """, bmi, interpretation, recommendations));

                        // Сохраняем результат в базе данных
                        saveBMIResult(chatId, gender, height, weight, bmi);

                        // Очистка состояния после завершения
                        userStates.remove(chatId);
                        userGenders.remove(chatId);
                        userHeights.remove(chatId);
                    } else {
                        sendMessage(chatId, "Вес должен быть больше 27кг и меньше 611кг. Попробуйте снова:");
                    }
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Ошибка: Пожалуйста, введите корректное число для веса:");
                }
                break;
        }
    }

    private void saveBMIResult(long chatId, String gender, double height, double weight, double bmi) {
        dbHandler.saveUserHistory(String.valueOf(chatId), gender, height, weight, bmi);
    }

    private boolean isValidGender(String gender) {
        return gender.equalsIgnoreCase("мужской") || gender.equalsIgnoreCase("женский");
    }

    private void requestHeight(long chatId) {
        sendMessage(chatId, "Введите ваш рост в сантиметрах (например, 175.5):");
    }

    private void requestWeight(long chatId) {

        sendMessage(chatId, "Введите ваш вес в килограммах (например, 70.5):");
    }


    private double calculateBMI(double weight, double height) {
        double heightInMeters = height / 100;
        return weight / (heightInMeters * heightInMeters);
    }

    private String interpretBMI(double bmi, String gender) {
        if (gender.equalsIgnoreCase("мужской")) {
            if (bmi < 16) return "🟪 гипотрофия 3-й степени";
            else if (bmi < 17) return "🟦 гипотрофия 2-й степени";
            else if (bmi < 18.5) return "🟧 гипотрофия 1-й степени";
            else if (bmi < 24.9) return "🟩 норма";
            else if (bmi == 20.8) return "⭐️ идеально";
            else if (bmi < 28.5) return "🟨 ожирение 1-й степени";
            else if (bmi < 39) return "🟧 ожирение 2-й степени";
            else return "🟥 ожирение 3-й степени";
        } else { // женский
            if (bmi < 16) return "🟪 гипотрофия 3-й степени";
            else if (bmi < 18) return "🟦 гипотрофия 2-й степени";
            else if (bmi < 20) return "🟧 гипотрофия 1-й степени";
            else if (bmi < 24.99) return "🟩 норма";
            else if (bmi == 22) return "⭐️ идеально";
            else if (bmi < 29.99) return "🟨 ожирение 1-й степени";
            else if (bmi < 39.99) return "🟧 ожирение 2-й степени";
            else return "🟥 ожирение 3-й степени";
        }
    }

    private String getRecommendations(double bmi, String gender) {
        if (gender.equalsIgnoreCase("мужской")) {
            if (bmi < 16) return "Обязательно проконсультируйтесь с врачом.";
            else if (bmi < 17) return "Обязательно проконсультируйтесь с врачом.";
            else if (bmi < 18.5) return "Проконсультируйтесь с врачом.";
            else if (bmi < 24.9) return "Отличный показатель! Поддерживайте здоровый образ жизни.";
            else if (bmi == 20.8) return "Идеальный показатель! Продолжайте здоровый образ жизни.";
            else if (bmi < 28.5) return "Обратите внимание на питание и физическую активность.";
            else if (bmi < 39)
                return "Обратите внимание на питание и физическую активность. Проконсультируйтесь с врачом.";
            else return "Обязательно проконсультируйтесь с врачом.";
        } else { // женский
            if (bmi < 16) return "Обязательно проконсультируйтесь с врачом.";
            else if (bmi < 18) return "Обязательно проконсультируйтесь с врачом.";
            else if (bmi < 20) return "Проконсультируйтесь с врачом.";
            else if (bmi < 24.99) return "Отличный показатель! Поддерживайте здоровый образ жизни.";
            else if (bmi == 22) return "Идеальный показатель! Продолжайте здоровый образ жизни.";
            else if (bmi < 29.99) return "Обратите внимание на питание и физическую активность.";
            else if (bmi < 39.99)
                return "Обратите внимание на питание и физическую активность. Проконсультируйтесь с врачом.";
            else return "Обязательно проконсультируйтесь с врачом.";
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("CallToPrintStackTrace")
    private void enableReminder(long chatId) {
        if (reminders.containsKey(chatId)) {
            sendMessage(chatId, "Напоминание уже включчено.");
            return;
        }
        Runnable reminderTask = () -> sendMessage(chatId, "Напоминание: Проверьте свой ИМТ с помощью команды /imt");

        try{
            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(reminderTask, 30, 30, TimeUnit.DAYS);
            reminders.put(chatId,future);
            sendMessage(chatId, "напоминание включено. Вы будете получать уведомление о проверке раз в месяц.");
            } catch (Exception e){
                 sendMessage(chatId, "Произошла ошибка.");
                 e.printStackTrace();
            }
    }

    private void disableReminder(long chatId) {
        ScheduledFuture<?> future = reminders.remove(chatId);
        if (future != null) {
            future.cancel(false);
            sendMessage(chatId, "Напоминание отключено.");
        } else {
            sendMessage(chatId, "Напоминание не было включено.");
        }
    }


    @Override
    public String getBotUsername() {
        return "IMT_BroadBone_bot"; // Замените на ваш username
    }

    @Override
    public String getBotToken() {
        return "7257019584:AAFxkPppXtime1uoOv3gn-HOcOrpfYBK3SU"; // Замените на ваш токен
    }
}
