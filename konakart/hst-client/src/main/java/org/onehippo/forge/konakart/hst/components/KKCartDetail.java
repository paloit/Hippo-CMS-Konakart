package org.onehippo.forge.konakart.hst.components;

import com.konakart.app.CreateOrderOptions;
import com.konakart.app.KKException;
import com.konakart.app.Option;
import com.konakart.appif.*;
import org.apache.commons.lang.StringUtils;
import org.hippoecm.hst.component.support.forms.FormMap;
import org.hippoecm.hst.component.support.forms.FormUtils;
import org.hippoecm.hst.core.component.HstComponentException;
import org.hippoecm.hst.core.component.HstRequest;
import org.hippoecm.hst.core.component.HstResponse;
import org.onehippo.forge.konakart.hst.vo.CartItem;

import java.util.LinkedList;
import java.util.List;

/**
 * This component is used to manage the detail cart page. You will be able to update quantity, to remove product, etc..
 * <p/>
 * You need to define a refid="cartDetailId" on the sitemap associated to the mycart.
 */
public class KKCartDetail extends KKHstActionComponent {

    /**
     * This action is used to add a remove a product
     */
    private static final String REMOVE_ACTION = "remove";


    @Override
    public void doBeforeRender(HstRequest request, HstResponse response) throws HstComponentException {
        super.doBeforeRender(request, response);

        /*
             * If the current customer has items in his basket, then we have to create a list of
             * CartItem objects and populate them since these are the objects that we will use to
             * display the basket items on the screen.
             */
        CustomerIf currentCustomer = getCurrentCustomer();

        try {
            if (getCurrentCustomer() != null && currentCustomer.getBasketItems() != null && currentCustomer.getBasketItems().length > 0) {

                // We update the basket with the quantities in stock
                BasketIf[] items = kkEngine.getEngine().updateBasketWithStockInfoWithOptions(
                        currentCustomer.getBasketItems(),
                        kkEngine.getBasketMgr().getAddToBasketOptions());

                /*
                * Create a temporary order to get order totals that we can display in the edit cart
                * screen. Comment this out if you don't want to show extra information such as
                * shipping and discounts before checkout.
                */
                OrderIf order = createTempOrder(getCurrentCustomer().getId(), items);


                String coupon = kkEngine.getOrderMgr().getCouponCode();
                String giftCertCode = kkEngine.getOrderMgr().getGiftCertCode();
                int rewardPoints = kkEngine.getOrderMgr().getRewardPoints();

                // Set the coupon code from the one saved in the order manager
                if (coupon != null) {
                    request.setAttribute("coupon", coupon);
                }
                // Set the GiftCert code from the one saved in the order manager
                if (giftCertCode != null) {
                    request.setAttribute("giftCertCode", giftCertCode);
                }

                // Set the reward points from the ones saved in the order manager
                if (rewardPoints != 0) {
                    request.setAttribute("rewardPoints", rewardPoints);
                }

                if  (order != null) {
                    order.setCouponCode(coupon);
                    order.setGiftCertCode(giftCertCode);
                    order.setPointsRedeemed(rewardPoints);
                }


                // Fill the CartItem list
                List<CartItem> cartItems = new LinkedList<CartItem>();
                request.setAttribute("cartitems", cartItems);

                for (BasketIf b : items) {
                    if (b != null && b.getProduct() != null) {
                        CartItem item = new CartItem(b.getId(), b.getProduct().getId(), b
                                .getProduct().getName(), b.getProduct().getImage(), b.getQuantity(),
                                b.getQuantityInStock());

                        if (kkEngine.displayPriceWithTax()) {
                            item.setTotalPrice(kkEngine.formatPrice(b.getFinalPriceIncTax()));
                        } else {
                            item.setTotalPrice(kkEngine.formatPrice(b.getFinalPriceExTax()));
                        }

                        // Set the options of the new CartItem
                        if (b.getOpts() != null && b.getOpts().length > 0) {
                            String[] optNameArray = new String[b.getOpts().length];
                            for (int j = 0; j < b.getOpts().length; j++) {
                                if (b.getOpts()[j] != null) {
                                    if (b.getOpts()[j].getType() == Option.TYPE_VARIABLE_QUANTITY) {
                                        optNameArray[j] = b.getOpts()[j].getName() + " "
                                                + b.getOpts()[j].getQuantity() + " "
                                                + b.getOpts()[j].getValue();
                                    } else {
                                        optNameArray[j] = b.getOpts()[j].getName() + " "
                                                + b.getOpts()[j].getValue();
                                    }
                                } else {
                                    optNameArray[j] = "";
                                }
                            }
                            item.setOptNameArray(optNameArray);
                        }


                        cartItems.add(item);
                    }
                }
            }
        } catch (KKException e) {
            log.warn("Unable to display the cart", e);
        }



        FormMap formMap = new FormMap();
        FormUtils.populate(request, formMap);
        request.setAttribute("form", formMap);
    }

    @Override
    public void doAction(String action, HstRequest request, HstResponse response) {

        if (StringUtils.equals(action, REMOVE_ACTION)) {
            //processRemove()


        }


    }

    /*
    * Populate checkout order with a temporary order created before the checkout process really
    * begins. If the customer hasn't registered or logged in yet, we use the default customer to
    * create the order.
    *
    * With this temporary order we can give the customer useful information on shipping costs and
    * discounts without him having to login.
    */
    private OrderIf createTempOrder(int custId, BasketIf[] items) {
        try {
            String sessionId = null;

            // Reset the checkout order
            kkEngine.getOrderMgr().setCheckoutOrder(null);

            CreateOrderOptionsIf options = new CreateOrderOptions();
            if (custId < 0) {
                options.setUseDefaultCustomer(true);
            } else {
                sessionId = kkEngine.getSessionId();
                options.setUseDefaultCustomer(false);
            }

            // Add extra info to the options
            FetchProductOptionsIf productOptions = kkEngine.getProductMgr().getFetchProdOptions();

            if (productOptions != null) {
                options.setPriceDate(productOptions.getPriceDate());
                options.setCatalogId(productOptions.getCatalogId());
                options.setUseExternalPrice(productOptions.isUseExternalPrice());
                options.setUseExternalQuantity(productOptions.isUseExternalQuantity());
            }

            // Create the order
            OrderIf order = kkEngine.getEngine().createOrderWithOptions(sessionId, items, options,
                    kkEngine.getLanguage().getId());

            if (order == null) {
                return null;
            }

            /*
             * We set the customer id to that of the guest customer so that promotions with
             * expressions are calculated correctly
             */
            if (custId < 0) {
                order.setCustomerId(kkEngine.getCustomerMgr().getCurrentCustomer().getId());
            }

            // Set the checkout order to be the new order
            kkEngine.getOrderMgr().setCheckoutOrder(order);

            // Get shipping quotes and select the first one
            kkEngine.getOrderMgr().createShippingQuotes();
            if (kkEngine.getOrderMgr().getShippingQuotes() != null
                    && kkEngine.getOrderMgr().getShippingQuotes().length > 0) {

                kkEngine.getOrderMgr().getCheckoutOrder().setShippingQuote(kkEngine.getOrderMgr().getShippingQuotes()[0]);
            }

            // Populate the checkout order with order totals
            kkEngine.getOrderMgr().populateCheckoutOrderWithOrderTotals();

            return order;

        } catch (Exception e) {
            // If the order can't be created we don't report back an exception
            if (log.isWarnEnabled()) {
                log.warn("A temporary order could not be created", e);
            }
        }

        return null;
    }
}
