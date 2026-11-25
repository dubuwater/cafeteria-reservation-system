package kr.ac.cbnu.cafeteria.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

/**
 * 행 잠금 도우미. 설계서 4.0절의 LOCK(SELECT … FOR UPDATE)을 구현한다.
 * 이미 읽어 둔 엔티티는 잠금과 함께 DB에서 다시 읽어(refresh), 잠금을 얻은 시점의 최신 값으로 검사하게 한다.
 * 잠금 순서는 예약 → 메뉴 → 시간대 → 식권으로 고정해 교착 상태를 막는다.
 */
@Component
public class LockSupport {

    private final EntityManager em;

    public LockSupport(EntityManager em) {
        this.em = em;
    }

    public <T> T lock(T entity) {
        @SuppressWarnings("unchecked")
        T target = (T) Hibernate.unproxy(entity);
        em.refresh(target, LockModeType.PESSIMISTIC_WRITE);
        return target;
    }

    public <T> T findForUpdate(Class<T> type, Object id) {
        return em.find(type, id, LockModeType.PESSIMISTIC_WRITE);
    }
}
