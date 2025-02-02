union sockaddr_u;

int get_default_ttl();

int get_addr(const char *str, union sockaddr_u *addr);

void *add(void **root, int *n, size_t ss);

void clear_params(void);

char *ftob(const char *str, ssize_t *sl);

char *data_from_str(const char *str, ssize_t *size);

size_t parse_cform(char *buffer, size_t blen, const char *str, size_t slen);

struct mphdr *parse_hosts(char *buffer, size_t size);

struct mphdr *parse_ipset(char *buffer, size_t size);

int parse_offset(struct part *part, const char *str);
