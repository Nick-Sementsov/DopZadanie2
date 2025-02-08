using System;

public class Programm
{
    public static void Main(string[] args)
    {
        // 1. Исходные данные

        // Начальное положение улитки на дереве (в сантиметрах)
        // Дерево высотой 5 метров = 500 см
        int nachalnoePolozhenie = 100;

        // Массив, хранящий информацию о погоде за 30 дней
        // 0 - пасмурно, 1 - солнечно
        int[] pogoda = new int[30];

        // 2. Заполняем массив pogoda случайными значениями (для примера)

        // Создаем генератор случайных чисел
        Random sluchaynost = new Random();

        // Цикл для заполнения массива pogoda случайными числами 0 или 1
        for (int i = 0; i < 30; i++)
        {
            pogoda[i] = sluchaynost.Next(0, 2); // 0 или 1
        }

        // 3. Выводим информацию о погоде на экран (для наглядности)

        Console.WriteLine("Погода за 30 дней:");

        // Цикл для вывода элементов массива pogoda на экран
        for (int i = 0; i < 30; i++)
        {
            if (pogoda[i] == 1)
            {
                Console.Write("Солнечно ");
            }
            else
            {
                Console.Write("Пасмурно ");
            }
        }
        Console.WriteLine(); // Переход на новую строку

        // 4. Вычисляем конечное положение улитки

        // Текущее положение улитки
        int tekusheePolozhenie = nachalnoePolozhenie;

        // Цикл для обработки каждого дня
        for (int i = 0; i < 30; i++)
        {
            if (pogoda[i] == 1) // Если день солнечный
            {
                tekusheePolozhenie += 5; // Поднимаемся на 5 см
            }
            else // Если день пасмурный
            {
                tekusheePolozhenie -= 3; // Опускаемся на 3 см
            }

            // Проверяем, не вышла ли улитка за пределы дерева
            if (tekusheePolozhenie < 0)
            {
                tekusheePolozhenie = 0; // Нижняя граница (земля)
            }
            else if (tekusheePolozhenie > 500)
            {
                tekusheePolozhenie = 500; // Верхняя граница (вершина дерева)
            }
        }

        // 5. Выводим результат на экран

        Console.WriteLine("Конечное положение улитки: " + tekusheePolozhenie + " см");

        // Для задержки экрана перед закрытием (чтобы можно было увидеть результат)
        Console.ReadKey();
    }
}