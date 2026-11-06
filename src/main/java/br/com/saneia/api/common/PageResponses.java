package br.com.saneia.api.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * Paginação compatível com o app: o corpo continua sendo um array JSON (contrato do mobile)
 * e os metadados vão nos cabeçalhos X-Total-Count, X-Total-Pages, X-Page e X-Page-Size.
 */
public final class PageResponses {

    public static final String TOTAL_COUNT = "X-Total-Count";
    public static final String TOTAL_PAGES = "X-Total-Pages";
    public static final String PAGE = "X-Page";
    public static final String PAGE_SIZE = "X-Page-Size";

    private PageResponses() {
    }

    public static <T, R> ResponseEntity<List<R>> of(Page<T> page, Function<T, R> mapper) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TOTAL_COUNT, String.valueOf(page.getTotalElements()));
        headers.add(TOTAL_PAGES, String.valueOf(page.getTotalPages()));
        headers.add(PAGE, String.valueOf(page.getNumber()));
        headers.add(PAGE_SIZE, String.valueOf(page.getSize()));
        return ResponseEntity.ok().headers(headers).body(page.getContent().stream().map(mapper).toList());
    }
}
