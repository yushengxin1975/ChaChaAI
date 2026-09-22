#include <stdio.h>
#include "include/pinyinime.h"

using namespace ime_pinyin;

int main() {
    printf("Testing im_open_decoder on pointer=%zu, size_t=%zu...\n", sizeof(void*), sizeof(size_t));
    bool ret = im_open_decoder("/tmp/dict_pinyin.dat", "/tmp/usr_dict.dat");
    printf("im_open_decoder result: %d\n", ret ? 1 : 0);
    if (ret) {
        size_t count = im_search("nihao", 5);
        printf("im_search('nihao') count: %zu\n", count);
        im_close_decoder();
    }
    return 0;
}
