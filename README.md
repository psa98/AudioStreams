# AudioStreams

Библиотека для Android, реализующая работу со звуком как с 16 битным PCM потоком, стандартным типом данных для его медиа-подсистсемы.
При работе со звуком в Андроиде имеется возможность либо работать на верхнем уровне, к примеру при помощи класса MediaPlayer, 
не имея никакой возможности вмешиваться в промежуточную обработку данных, либо на чрезвычайно сложном для реализации 
низком уровне - непосредственно с кодеками, муксером, классами AudioTrack, AudioRecord. 

Данная библиотека сводит работу с аудио получаемым из файлов или с микрофона и выводимым в дикамики или mp3 файл к работе с бинарными
Input/OutputStreams стандартных 16 битных отсчетов звука. Это позволит легко хранить и обрабатывать звуковые клипы в памяти и 
использовать сторонние библиотеки (распознавания голоса, эффектов), работающие со звуком с потоками или массивами PCM.   
